package com.company.fylisten.fylisten2.manager;


import android.util.ArraySet;

import androidx.annotation.NonNull;


import com.company.fylisten.fylisten2.annotations.Status;
import com.company.fylisten.fylisten2.listeners.Listener;
import com.company.fylisten.fylisten2.publisher.LifecyclePublisher;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 负责管理订阅者与发布者
 * <p>
 * 对map的双重检测
 * 外层检测是为了加快速度（不需要修改临界区内容时，不去争锁）
 * 内层检测是为了获得锁之后，临界区的内容可能被改变，需要再次检查
 * volatile是为了防止指令重排，导致使用非空的instance是个半初始化状态的对象
 */
public class FyListen {

    /**
     * 用于对map这个临界资源的控制
     */
    private final ReentrantLock lock = new ReentrantLock();


    /**
     * 弱引用map，当发布者不被引用的时候，将其释放，并删除entry（即同时删除listener的引用）
     */
    private final WeakHashMap<LifecyclePublisher, PublisherNotebook> map = new WeakHashMap<>();

    /**
     * 单例
     */
    private FyListen() {

    }

    /**
     * DCL+volatile单例模式
     */
    private volatile static FyListen instance;

    /**
     * 只供内部使用
     */
    private static FyListen getInstance() {
        if (instance == null) {
            synchronized (FyListen.class) {
                if (instance == null) {
                    instance = new FyListen();
                }
            }
        }
        return instance;
    }

    /**
     * 发布者进行发布注册
     * lock保证临界区map资源线程安全
     */
    public static boolean registerPublisher(@NonNull LifecyclePublisher o) {
        FyListen fl = FyListen.getInstance();
        //如果已经注册过，就不重复注册了，也不用争抢锁
        if (fl.map.containsKey(o)) return false;
        fl.lock.lock();
        boolean registerSuccess = false;
        try {
            //再次判断，避免在获取锁的等待过程中o已经被添加过，这里不需要volatile防重排
            // 我们认为 o 到这已经实例化好了
            if (!fl.map.containsKey(o)) {
                //如果确实没注册过，就注册一下
                fl.map.put(o, new PublisherNotebook());
                registerSuccess = true;
            }
        } finally {
            fl.lock.unlock();
        }
        return registerSuccess;
    }

    /**
     * 发布者取消发布注册
     * 并通知其所有监听者，“我不再对外宣布我的生命周期”
     * lock保证临界区map资源线程安全
     */
    public static boolean unregisterPublisher(@NonNull LifecyclePublisher o) {
        FyListen fl = FyListen.getInstance();
        //如果本来就不存在，就返回 unregister失败
        if (fl.map.containsKey(o)) return false;
        //如果存在，上锁
        fl.lock.lock();
        boolean unregisterSuccess = false;
        try {
            //再次判断
            if (fl.map.containsKey(o)) {
                //如果确实还在map中，就取消注册
                //并通知其所有监听者，“我不再对外宣布我的生命周期”
                PublisherNotebook publisherNotebook = fl.map.get(o);
                if (publisherNotebook != null && publisherNotebook.getListeners() != null) {
                    for (Listener listener : publisherNotebook.getListeners()) {
                        if (listener != null) {
                            listener.onError(o, "publisher no longer publishes");
                        }
                    }
                }
                //然后再从map中取消注册
                //主动抛弃所有listener的引用
                fl.map.remove(o);
                unregisterSuccess = true;
            }
        } finally {
            fl.lock.unlock();
        }
        return unregisterSuccess;
    }


    /**
     * 注册监听者，并强制尝试将target进行注册
     * 此方法默认不回调target当前的生命周期状态（target最近发布的生命周期通知）
     */
    public static void registerListenerAnyway(@NonNull LifecyclePublisher target, @NonNull Listener listener) {
        FyListen.registerListenerAnyway(target, listener, false);
    }

    /**
     * 注册监听者，并强制尝试将target进行注册
     *
     * @param wantCurrentStatusCallback 是否需要立即回调target当前的生命周期状态（target最近发布的生命周期通知）
     */
    public static void registerListenerAnyway(@NonNull LifecyclePublisher target, @NonNull Listener listener, boolean wantCurrentStatusCallback) {
        FyListen fl = FyListen.getInstance();
        fl.lock.lock();
        try {
            //如果有这个键，才可以往里添加
            if (fl.map.containsKey(target)) {
                PublisherNotebook publisherNotebook = fl.map.computeIfAbsent(target, lifecyclePublisher -> new PublisherNotebook());
                //如果出了意外没有arraylist为value，就为它添加一个
                publisherNotebook.listeners.add(listener);
                if (wantCurrentStatusCallback) {
                    //如果希望一注册就回调
                    sendLifecycleCallback(target, publisherNotebook.status);
                }
            } else {
                //如果这个发布者不存在
                //将注册这个发布者
                //同一个线程，可重入
                registerPublisher(target);
                //如果上面注册失败了，尝试第二次的安全添加
                //第二次使用安全添加，如果还是败了，不再尝试，返回error通知用户publisher创建失败
                registerListenerSafely(target, listener);
            }
        } finally {
            fl.lock.unlock();
        }
    }


    /**
     * 注册监听者，如果target没有进行发布注册，将坚挺失败，并通过 onError() 回调报告错误
     * 此方法默认不回调target当前的生命周期状态（target最近发布的生命周期通知）
     */
    public static void registerListenerSafely(@NonNull LifecyclePublisher target, @NonNull Listener listener) {
        FyListen.registerListenerSafely(target, listener, false);
    }

    /**
     * 注册监听者，如果target没有进行发布注册，将坚挺失败，并通过 onError() 回调报告错误
     * 不会立即回调target当前的生命周期状态（target最近发布的生命周期通知）
     *
     * @param wantCurrentStatusCallback 是否需要立即回调target当前的生命周期状态（target最近发布的生命周期通知）
     */
    public static void registerListenerSafely(@NonNull LifecyclePublisher target, @NonNull Listener listener, boolean wantCurrentStatusCallback) {
        FyListen fl = FyListen.getInstance();
        fl.lock.lock();
        try {
            //如果有这个键，才可以往里添加
            if (fl.map.containsKey(target)) {
                PublisherNotebook publisherNotebook = fl.map.computeIfAbsent(target, lifecyclePublisher -> new PublisherNotebook());
                //如果出了意外没有arraylist为value，就为它添加一个
                publisherNotebook.listeners.add(listener);
                if (wantCurrentStatusCallback) {
                    //如果希望一注册就回调
                    sendLifecycleCallback(target, publisherNotebook.status);
                }
            } else {
                //如果这个发布者不存在
                listener.onError(target, "error: publisher doesn't exist");
            }
        } finally {
            fl.lock.unlock();
        }
    }

    /**
     * 监听者取消对某个发布者的监听
     * 避免监听者自身的内存泄漏
     */
    public static void unregisterListener(@NonNull LifecyclePublisher target, @NonNull Listener listener) {
        FyListen fl = FyListen.getInstance();
        fl.lock.lock();
        try {
            //找到目标发布者，在其中取消自己的监听注册
            if (fl.map.containsKey(target)) {
                PublisherNotebook publisherNotebook = fl.map.get(target);
                if (publisherNotebook != null && publisherNotebook.getListeners() != null) {
                    publisherNotebook.getListeners().remove(listener);
                }
            }
        } finally {
            fl.lock.unlock();
        }
    }

    /**
     * 监听者取消对所有发布者的监听
     * 避免监听者自身的内存泄漏
     */
    public static void unregisterListenerAll(@NonNull Listener listener) {
        FyListen fl = FyListen.getInstance();
        fl.lock.lock();
        try {
            for (Map.Entry<LifecyclePublisher, PublisherNotebook> entry : fl.map.entrySet()) {
                if (entry == null || entry.getKey() == null) continue;
                PublisherNotebook notebook = entry.getValue();
                if (notebook != null && notebook.getListeners() != null) {
                    notebook.getListeners().remove(listener);
                }
            }
        } finally {
            fl.lock.unlock();
        }
    }

    /**
     * 发布消息
     * 发布消息的时候，我不希望list被修改
     * 也就是不希望有监听者新添加进来
     */
    public static void sendLifecycleCallback(@NonNull LifecyclePublisher from, @Status int newStatus) {
        //同一个线程可重入
        //同一个publisher连续发布两个状态，由于在同一个线程，会同步执行，不会出问题
        FyListen fl = FyListen.getInstance();
        fl.lock.lock();
        try {
            //如果有这个键，才可以往里添加
            PublisherNotebook publisherNotebook;
            if (fl.map.containsKey(from) && (publisherNotebook = fl.map.get(from)) != null) {
                //设为最新状态
                publisherNotebook.status = newStatus;
                switch (newStatus) {
                    case Status.INITIALED:
                        //发布者刚注册进来，还没有消息发布
                        break;
                    case Status.ON_ATTACH:
                        for (Listener l : publisherNotebook.listeners) {
                            if (l != null)
                                l.onAttach(from);
                        }
                        break;
                    case Status.ON_CREATE:
                        for (Listener l : publisherNotebook.listeners) {
                            if (l != null)
                                l.onCreate(from);
                        }
                        break;
                    case Status.ON_CREATE_VIEW:
                        for (Listener l : publisherNotebook.listeners) {
                            if (l != null)
                                l.onCreateView(from);
                        }
                        break;
                    case Status.ON_START:
                        for (Listener l : publisherNotebook.listeners) {
                            if (l != null)
                                l.onStart(from);
                        }
                        break;
                    case Status.ON_RESUME:
                        for (Listener l : publisherNotebook.listeners) {
                            if (l != null)
                                l.onResume(from);
                        }
                        break;
                    case Status.ON_PAUSE:
                        for (Listener l : publisherNotebook.listeners) {
                            if (l != null)
                                l.onPause(from);
                        }
                        break;
                    case Status.ON_STOP:
                        for (Listener l : publisherNotebook.listeners) {
                            if (l != null)
                                l.onStop(from);
                        }
                        break;
                    case Status.ON_DESTROY_VIEW:
                        for (Listener l : publisherNotebook.listeners) {
                            if (l != null)
                                l.onDestroyView(from);
                        }
                        break;
                    case Status.ON_DESTROY:
                        for (Listener l : publisherNotebook.listeners) {
                            if (l != null)
                                l.onDestroy(from);
                        }
                        break;
                    case Status.ON_DETACH:
                        for (Listener l : publisherNotebook.listeners) {
                            if (l != null)
                                l.onDetach(from);
                        }
                        break;
                }
            }
        } finally {
            fl.lock.unlock();
        }
    }

    /**
     * 发布者的记事本
     * 记录了当前来到了哪个生命周期状态
     * 以及手下有哪些监听者
     */
    private static class PublisherNotebook {
        private @Status
        int status;
        private Set<Listener> listeners;

        public PublisherNotebook() {
            //使用arrayset进行内存优化
            listeners = new ArraySet<>();
            status = Status.INITIALED;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public Set<Listener> getListeners() {
            return listeners;
        }

        public void setListeners(Set<Listener> listeners) {
            this.listeners = listeners;
        }
    }


}
