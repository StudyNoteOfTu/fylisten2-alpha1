package com.company.fylisten.fylisten2.listeners;


import com.company.fylisten.fylisten2.publisher.LifecyclePublisher;

/**
 * 监听者接口
 */
public interface Listener {

    default void onAttach(LifecyclePublisher p) {
    }

    default void onCreate(LifecyclePublisher p) {
    }

    default void onCreateView(LifecyclePublisher p) {
    }

    default void onStart(LifecyclePublisher p) {
    }

    default void onResume(LifecyclePublisher p) {
    }

    default void onPause(LifecyclePublisher p) {
    }

    default void onStop(LifecyclePublisher p) {
    }

    default void onDestroy(LifecyclePublisher p) {
    }

    default void onDestroyView(LifecyclePublisher p) {
    }

    default void onDetach(LifecyclePublisher p) {
    }

    //发布者不存在或者死亡，必须重写
    void onError(LifecyclePublisher p, String error);

}
