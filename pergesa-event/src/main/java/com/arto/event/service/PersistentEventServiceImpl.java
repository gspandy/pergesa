/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.arto.event.service;

import com.arto.event.bootstrap.Event;
import com.arto.event.bootstrap.EventBusFactory;
import com.arto.event.bootstrap.EventContext;
import com.arto.event.common.Constants;
import com.arto.event.common.EventStatusEnum;
import com.arto.event.config.ConfigManager;
import com.arto.event.exception.EventException;
import com.arto.event.exception.PersistentEventLockException;
import com.arto.event.serialization.Serializer;
import com.arto.event.storage.EventInfo;
import com.arto.event.storage.EventStorage;
import com.arto.event.util.DateUtil;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Random;

/**
 * Created by xiong.j on 2017/1/4.
 */
@Slf4j
@Service("persistentEventService")
public class PersistentEventServiceImpl implements PersistentEventService {

    /** 事件持久化操作 */
    @Autowired
    private EventStorage eventStorage;

    /** 随机数生成器，用来分隔Tag */
    private Random random = new Random();

    /**
     * 持久化Event
     *
     * @param event
     * @param serializer
     * @param type
     * @throws
     */
    @Transactional
    @Override
    public void persist(Event event, Serializer serializer, String type) throws EventException {
        if (Strings.isNullOrEmpty(event.getBusinessId()) || Strings.isNullOrEmpty(event.getBusinessType())) {
            throw new EventException("'businessId' and 'businessType' can't be null or blank.");
        }
        if (Strings.isNullOrEmpty(type)) {
            throw new EventException("'eventType' can't be null or blank.");
        }

        try {
            EventInfo eventInfo = eventStorage.create(event2Info(event, serializer, type));
            if (eventInfo.getId() != -1) {
                event.setEventContext(new EventContext(eventInfo));
            }
        } catch (Exception e) {
            throw new EventException("Persist event failed.", e);
        }
    }

    /**
     * 对持久化Event加锁
     *
     * @param eventInfo
     * @return
     * @throws
     */
    @Override
    public EventInfo lock(EventInfo eventInfo) throws EventException {
        if (ConfigManager.getBoolean("event.persistent.lock.optimistic", false)) {
            // 乐观锁直接返回
            return null;
        } else {
            try {
                // 手动加锁
                return eventStorage.lockById(eventInfo.getId());
                /** } catch (EmptyResultDataAccessException empty) {
                ThreadUtil.sleep(100); */
            } catch (Exception e) {
                if (e.getMessage().contains("ORA-00054")
                        || e.getMessage().contains("could not obtain lock")) {
                    // Oracle和Postgresql环境下获取锁失败Exception
                    throw new PersistentEventLockException(e);
                }
                // 其它Exception
                throw new EventException("Lock persistent event failed.", e);
            }
        }
    }

    /**
     * 持久化Event处理失败时的处理
     *
     * @param eventInfo
     */
    @Override
    public void fail(EventInfo eventInfo){
        // TODO 如果没有启用事务消息(2PC)， 失败消息直接发后管?
        if (eventInfo.getDefaultRetriedCount() == -1) {
            // 无限重试时继续重试
            retry(eventInfo);
        }
        if (eventInfo.getCurrentRetriedCount() == eventInfo.getDefaultRetriedCount()) {
            // 到达重试次数时
            eventInfo.setStatus(EventStatusEnum.MANUAL_WAIT.getCode());
            // 1.通过MQ发送到后管系统
            report(eventInfo);
            // 2.更新处理状态为 "3:等待人工处理"
            EventInfo updInfo = new EventInfo();
            updInfo.setId(eventInfo.getId());
            updInfo.setStatus(eventInfo.getStatus());
            update(updInfo);
        } else {
            // 继续重试
            retry(eventInfo);
        }
    }

    /**
     * 持久化Event处理成功时的处理
     *
     * @param eventInfo
     */
    @Override
    public void finish(EventInfo eventInfo){
        // 更新处理状态为 "2:处理成功"
        EventInfo updInfo = new EventInfo();
        updInfo.setId(eventInfo.getId());
        updInfo.setStatus(EventStatusEnum.SUCCESS.getCode());
        update(updInfo);
    }

    private void retry(EventInfo eventInfo){
        EventInfo updInfo = new EventInfo();
        updInfo.setId(eventInfo.getId());
        if (eventInfo.getStatus() != EventStatusEnum.PROCESSING.getCode()) {
            // 更新状态为 "1:处理中"
            updInfo.setStatus(EventStatusEnum.PROCESSING.getCode());
        }

        updInfo.setCurrentRetriedCount(eventInfo.getCurrentRetriedCount() + 1);
        if (eventInfo.getDefaultRetriedCount() == -1) {
            // 无限重试时(默认间隔为10分钟)
            updInfo.setNextRetryTime(DateUtil.getPrevSecTimestamp(ConfigManager.getInt("event.infinite.retry.interval", 600)));
        } else {
            // 设置有重试次数时
            updInfo.setNextRetryTime(getNextRetryTime(updInfo.getCurrentRetriedCount()));
        }
        // 更新重试信息
        update(updInfo);
    }

    private void update(EventInfo updInfo) {
        if (ConfigManager.getBoolean("event.persistent.lock.optimistic", false)) {
            // 采用乐观锁时更新操作
            eventStorage.optimisticUpdate(updInfo);
        } else {
            // 采用悲观锁或其它情况下的更新操作
            eventStorage.update(updInfo);
        }
    }

    private void report(EventInfo eventInfo){
        Event<EventInfo> event = new Event<EventInfo>();
        event.setPayload(eventInfo);

        try {
            event.setGroup(Class.forName(Constants.REPORT_EVENT));
            EventBusFactory.getInstance().post(event);
        } catch (Throwable t) {
            log.warn("Report event failed. eventInfo" + eventInfo, t);
            // 报告失败更新重试时间，避免调度任务立即抓取
            EventInfo updInfo = new EventInfo();
            updInfo.setId(eventInfo.getId());
            updInfo.setNextRetryTime(DateUtil.getPrevSecTimestamp(ConfigManager.getInt("event.infinite.retry.interval", 600)));
            update(updInfo);
        }
    }

    private EventInfo event2Info(Event event, Serializer serializer, String type) throws Exception {
        EventInfo info = new EventInfo();
        // Tag(事件分片数)
        info.setTag(random.nextInt(ConfigManager.getInt("event.storage.tag", 10)));
        // 系统名
        info.setSystemId(ConfigManager.getString("sar.name", "webapp"));
        // 业务流水号
        info.setBusinessId(event.getBusinessId());
        // 业务类型
        info.setBusinessType(event.getBusinessType());
        // 事件类型
        info.setEventType(type);
        // 事件状态
        info.setStatus(EventStatusEnum.WAIT.getCode());
        // 事件内容 使用传入的方式序列化
        info.setPayload(serializer.serializer(event));
        // 重试次数
        if (event.isPersistent() && event.getRetry() == 0) {
            // 持久化事件且没有设定重试次数的情况下，使用默认次数
            info.setDefaultRetriedCount(ConfigManager.getInt("event.retry.times", 5));
        } else {
            info.setDefaultRetriedCount(event.getRetry());
        }
        return info;
    }

    private Timestamp getNextRetryTime(int currentRetriedCount){
        long delayMsec = 600 * 1000; // 10分钟
        switch (currentRetriedCount){
            case 1: // 10分钟
                break;
            case 2: // 30分钟
                delayMsec = 1800 * 1000;
                break;
            case 3: // 1小时
                delayMsec = 3600 * 1000;
                break;
            case 4: // 6小时
                delayMsec = 6 * 3600 * 1000;
                break;
            case 5: // 12小时
                delayMsec = 12 * 3600 * 1000;
                break;
            case 6: // 24小时
                delayMsec = 24 * 3600 * 1000;
                break;
            case 7: // 36小时
                delayMsec = 36 * 3600 * 1000;
                break;
        }
        /*
            // 获取下一次重试时间，默认使用重试次数的9次方
            return new Timestamp(System.currentTimeMillis()
                + Math.round(Math.pow(9, currentRetriedCount))
                * 1000);
        */
        return new Timestamp(System.currentTimeMillis() + delayMsec);
    }
}
