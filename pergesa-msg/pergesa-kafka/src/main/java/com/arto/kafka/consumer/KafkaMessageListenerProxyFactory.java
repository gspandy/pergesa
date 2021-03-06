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
package com.arto.kafka.consumer;

import com.arto.core.consumer.MqListener;
import com.arto.event.router.PersistentEventRouterFactory;
import com.google.common.base.Strings;
import javassist.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by xiong.j on 2017/1/22.
 */
public class KafkaMessageListenerProxyFactory {

    private static final ConcurrentMap<String, Class<?>> CLASSES = new ConcurrentHashMap<String, Class<?>>();

    private static final ConcurrentMap<Class<?>, Object> INSTANCES = new ConcurrentHashMap<Class<?>, Object>();

    /**
     * 根据event类型生成对应的路由代理
     *
     * @param listener
     * @param type
     * @return 代理对象
     */
    public static <T> T getProxy(MqListener listener, String type) throws Throwable {
        return getInstance(getProxyClass(listener, type));
    }

    private static <T> T getInstance(Class<?> clazz) throws Throwable {
        if (clazz == null) {
            return null;
        }

        T instance = (T) INSTANCES.get(clazz);
        if (instance == null) {
            INSTANCES.putIfAbsent(clazz, (T) clazz.newInstance());
            instance = (T) INSTANCES.get(clazz);
        }

        return instance;
    }

    private static Class<?> getProxyClass(MqListener listener, String type) throws Throwable{
        if (listener == null || Strings.isNullOrEmpty(type)) {
            return null;
        }

        String className = getClassName(type);
        Class<?> newCls = getProxyClassFromCache(className);
        if (newCls != null) {
            return newCls;
        }

        // Class append
        ClassPool pool = new ClassPool(true);
        pool.appendClassPath(new LoaderClassPath(getClassloader()));
        CtClass cc = pool.makeClass(className + "_Listener_" + "Stub");
        cc.addInterface(pool.get(KafkaMessageListenerProxy.class.getName()));

        // Append single mehtod
        StringBuilder sb = new StringBuilder();
        sb.append("public void onMessage").append("(com.arto.core.consumer.MqListener listener, String message) throws Throwable { ");
        sb.append("listener.onMessage(com.alibaba.fastjson.JSON.parseObject(message, new com.alibaba.fastjson.TypeReference<com.arto.core.common.MessageRecord<")
                .append(type).append(">>(){});");
        sb.append("}");
        // System.out.println(sb.toString());
        CtMethod mthd = CtNewMethod.make(sb.toString(), cc);
        cc.addMethod(mthd);
        sb.setLength(0);

        //生成Class
        newCls = cc.toClass();
        CLASSES.putIfAbsent(className, newCls);

        return newCls;
    }

    private static Class<?> getProxyClassFromCache(String className){
        return CLASSES.get(className);
    }

    private static ClassLoader getClassloader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) classLoader = PersistentEventRouterFactory.class.getClassLoader();
        return classLoader;
    }

    private static String getClassName(String type) {
        return type.substring(type.lastIndexOf(".") + 1);
    }
}
