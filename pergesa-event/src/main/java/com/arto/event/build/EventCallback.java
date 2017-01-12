package com.arto.event.build;

/**
 * Created by xiong.j on 2017/1/11.
 */
public interface EventCallback {

    /**
     * 异步事件处理完成时的回调接口
     *
     * @param event
     */
    public void onCompletion(Event event);
}