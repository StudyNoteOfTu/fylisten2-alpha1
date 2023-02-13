package com.company.fylisten.fylisten2.aspects;

import android.util.Log;

import com.company.fylisten.fylisten2.annotations.LifecycleTrace;
import com.company.fylisten.fylisten2.annotations.Status;
import com.company.fylisten.fylisten2.manager.FyListen;
import com.company.fylisten.fylisten2.publisher.LifecyclePublisher;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

@Aspect
public class LifecycleTraceAspect {

    public static final boolean needRegisterByDefault = false;
    private static final String TAG = "lifecycle trace aspect";

    @Pointcut("execution(@com.company.lifecycle2.fylisten2.annotations.LifecycleTrace * *(..))")
    public void methodAnnotatedWithLifecycleTrace(){}


    @Before("methodAnnotatedWithLifecycleTrace()")
    public void joinPoint(JoinPoint joinPoint) throws Throwable{
        //ProcessJoinPoint只能用在around

        //如果当前的发布者还没注册过，根据设置来看是否要注册
        //不允许切片在一个非ListenerPublisher的实例上
        Object o = joinPoint.getTarget();
        if (!LifecyclePublisher.class.isAssignableFrom(o.getClass())){
            //如果切片注解不在publisher实现类中，不予处理
            Log.e(TAG,"切片注解不在publisher实现类中，不予处理");
            return;
        }else{
            //如果切片注解在publisher实现类中
            if (needRegisterByDefault){
                FyListen.registerPublisher((LifecyclePublisher) o);
            }
            //默认不会自动将带LifecycleTrace接口的类进行注册
            //要么registerListenerAnyway强制注册
            //要么registerPublisher主动注册
            Log.e(TAG,"发布消息");
            //发布消息 - 同时将最新状态更新到 publisher book 中
            //拿到这个方法
            MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
            Method method = methodSignature.getMethod();
            //拿到注解
            LifecycleTrace annotation = method.getAnnotation(LifecycleTrace.class);
            if (annotation!=null && annotation.value()!= Status.INITIALED){
                FyListen.sendLifecycleCallback((LifecyclePublisher)o,annotation.value());
            }
        }

    }
}
