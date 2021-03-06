package com.arto.event.service;

import com.alibaba.fastjson.JSON;
import com.arto.event.storage.EventInfo;

/**
 * Created by xiong.j on 2016/12/28.
 */
public class TransactionMessageProducerTest  {

    public static void main(String args[]){
        EventInfo e = new EventInfo();
        e.setId(1);
        e.setMemo("Haha");

        TransactionMessageProducerTest ec = new TransactionMessageProducerTest();
        ec.send(e, "key", new Object());

        EventInfo e1 = JSON.parseObject("{\"memo\":\"Haha\",\"id\":1,\"tag\":0,\"status\":-1}", EventInfo.class);
        System.out.println(e1.getMemo());
    }

    public boolean send(Object producerObject, String key, Object messge) {
        System.out.println(JSON.toJSON(producerObject));
        return false;
    }
}