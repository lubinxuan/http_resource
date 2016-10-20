package com.adtime.http.resource.util;

import com.adtime.http.resource.Request;
import com.adtime.http.resource.Result;
import com.adtime.http.resource.WebConst;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by xuanlubin on 2016/10/20.
 */
public class ConnectionAbortUtils {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionAbortUtils.class);

    //利用DNS服务检测网络是否可用
    private static final String ip = System.getProperty("network.check.ip", "114.114.114.114");

    private static final AtomicBoolean checkNetwork = new AtomicBoolean(false);

    private static long lastInActive = -1;

    private static final Runnable checkNetworkRunnable = () -> {

        logger.warn("Start Network Check Thread");


        //更新上次网络故障时间
        lastInActive = System.currentTimeMillis();

        while (true) {
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress(ip, 53));
                socket.close();

                checkNetwork.set(false);

                synchronized (checkNetwork) {
                    checkNetwork.notifyAll();

                }
                logger.warn("Network stable");

                break;
            } catch (IOException e) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }
        }
    };

    /**
     * 根据请求返回的状态以及错误信息判断是否网络中断导致本次请求异常
     *
     * @param result  请求结果
     * @param request 请求信息
     * @return
     */
    public static boolean isNetworkOut(Result result, Request request) {

        if (result.getStatus() != WebConst.HTTP_ERROR) {
            return false;
        }

        boolean isNetworkOut = StringUtils.contains(result.getMessage(), "Network is unreachable");
        if (isNetworkOut) {
            if (checkNetwork.compareAndSet(false, true)) {
                new Thread(checkNetworkRunnable).start();
            }

            checkNetworkStatus();
            return true;
        }

        //判断请求时间与上次网络故障时间
        if (request.getHttpExecStartTime() < lastInActive) {
            logger.debug("request start before last network inactive time");
            return true;
        }

        return false;
    }


    /**
     * 判定网络状态，如果网络不可用，线程进入等待状态
     */
    public static void checkNetworkStatus() {
        if (checkNetwork.get()) {
            logger.warn("Network is unreachable wait it stable");
            synchronized (checkNetwork) {
                if (checkNetwork.get()) {
                    try {
                        checkNetwork.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }


    /**
     * 更具数据开始读取的时间判断是否网络中断
     *
     * @param readStartTime 数据读取时间
     * @return
     */
    public static boolean checkNetworkStatus(long readStartTime) {
        return readStartTime < lastInActive || checkNetwork.get();
    }
}
