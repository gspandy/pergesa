package com.arto.core.common;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by xiong.j on 2017/1/13.
 */
@Setter
@Getter
@ToString
public class MessageRecord<T> {

    /** 自定义消息头*/
    private Map<String, Object> properties;

    /** 业务凭证流水号 */
    private String businessId;

    /** 业务类型 */
    private String businessType;

    /** 消息选择Key */
    @Deprecated
    private String selectKey;

    /**
     *  消息中件间生成的Id
     *  Kafka: 'K'+ 分区 + '_' + offset
     *  AMQ  : messageId
     **/
    private String messageId;

    /** 消息内容 */
    private T message;

//    /** 事务 启用后模拟消息两阶段提交 */
//    transient private boolean transaction;

    public MessageRecord(){}

    public MessageRecord(T message) {
        this.message = message;
    }

    public MessageRecord(String businessId, String businessType, T message) {
        this.businessId = businessId;
        this.businessType = businessType;
        this.message = message;
    }

    private synchronized void createProperties() {
        if (properties == null) {
            this.properties = new HashMap<String, Object>();
        }
    }

    private void setProperty(String key, Object value) {
        if (properties == null) {
            createProperties();
        }
        properties.put(key, value);
    }

    public void setBooleanProperty(String key, boolean value) {
        setProperty(key, value);
    }

    public boolean getBooleanProperty(String key){
        return (Boolean)(properties.get(key));
    }

    public void setIntProperty(String key, int value) {
        setProperty(key, value);
    }

    public int getIntProperty(String key){
        return (Integer)(properties.get(key));
    }

    public void setLongProperty(String key, long value) {
        setProperty(key, value);
    }

    public long getLongProperty(String key){
        return (Long)(properties.get(key));
    }

    public void setStringProperty(String key, String value) {
        setProperty(key, value);
    }

    public String getStringProperty(String key){
        return (String)(properties.get(key));
    }

}
