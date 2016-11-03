package com.adtime.http.resource.dns;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.util.ConcurrentHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.net.util.IPAddressUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by xuanlubin on 2016/9/8.
 */
public class DnsPreFetchUtils {

    private static final Logger logger = LoggerFactory.getLogger(DnsPreFetchUtils.class);

    private static final ConcurrentHashSet<String> DOMAIN_FILTER = new ConcurrentHashSet<>();

    private static final List<DnsUpdateInfo> DOMAIN_FETCH_QUEUE = new ArrayList<>(65535);

    private static final ExecutorService SERVICE = Executors.newFixedThreadPool(3);

    private static final long UPDATE_REQUIRE_TIME = 900000;

    private static class DnsUpdateInfo {
        private String domain;
        private long createTime;
    }

    static {

        String dnsServers = System.getProperty("dns.server", "114.114.114.114,8.8.8.8");
        String[] _dnsServers = dnsServers.split(",");


        DNSService.init(_dnsServers, 2000);

        AtomicInteger count = new AtomicInteger(0);

        new Timer("DnsInfoUpdate").schedule(new TimerTask() {
            @Override
            public void run() {
                for (Iterator<DnsUpdateInfo> iterator = DOMAIN_FETCH_QUEUE.iterator(); iterator.hasNext(); ) {
                    DnsUpdateInfo updateInfo = iterator.next();
                    if (updateInfo.createTime < System.currentTimeMillis() - UPDATE_REQUIRE_TIME) {
                        updateDnsInfo(updateInfo, false);
                    }
                }

                if (count.compareAndSet(10, 0)) {
                    try {
                        DnsCache.storeDnsCacheAsFile();
                    } catch (IOException e) {
                        logger.error("DNS缓存文件化异常", e);
                    }
                } else {
                    count.incrementAndGet();
                }
            }
        }, 5000, 5000);

    }

    private static InetAddress[] updateDnsInfo(DnsUpdateInfo updateInfo, boolean sync) {

        Callable<InetAddress[]> runnable = () -> {
            if (updateInfo.createTime > System.currentTimeMillis() - UPDATE_REQUIRE_TIME / 2) {
                return DnsCache.getCacheDns(updateInfo.domain);
            }
            InetAddress[] addresses = queryDns(updateInfo.domain);
            if (null != addresses && addresses.length > 0) {
                updateInfo.createTime = System.currentTimeMillis();
            }
            return addresses;
        };

        Future<InetAddress[]> future = SERVICE.submit(runnable);

        if (sync) {
            try {
                return future.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
        return null;
    }


    public static InetAddress[] queryDns(String host) {

        if (IPAddressUtil.isIPv4LiteralAddress(host)) {
            try {
                return InetAddress.getAllByName(host);
            } catch (UnknownHostException e) {
                return null;
            }
        }

        host = host.toLowerCase();

        try {
            List<String> resultList = DNSService.search("A", host);
            if (!resultList.isEmpty()) {
                InetAddress[] addresses = new InetAddress[resultList.size()];
                for (int i = 0; i < resultList.size(); i++) {
                    try {
                        addresses[i] = InetAddress.getByName(resultList.get(i));
                    } catch (UnknownHostException ignore) {
                    }
                }
                DnsCache.cacheDns(host, addresses);
                return addresses;
            } else {
                InetAddress[] addresses = InetAddress.getAllByName(host);
                if (null != addresses) {
                    DnsCache.cacheDns(host, addresses);
                    return addresses;
                }
            }
            logger.error("Can't get dns info of [{}]", host);
        } catch (Exception ignore) {
            logger.warn("DNS 信息获取异常 {}", ignore.toString());
        }
        return null;
    }


    public static void preFetch(String domain) {
        _preFetch(domain, false);
    }

    public static InetAddress[] _preFetch(String domain, boolean sync) {

        if (StringUtils.isBlank(domain)) {
            return null;
        }

        if (IPAddressUtil.isIPv4LiteralAddress(domain)) {
            try {
                return InetAddress.getAllByName(domain);
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        }

        if (DOMAIN_FILTER.add(domain.toLowerCase())) {
            DnsUpdateInfo dnsUpdateInfo = new DnsUpdateInfo();
            dnsUpdateInfo.domain = domain;
            dnsUpdateInfo.createTime = -1;
            DOMAIN_FETCH_QUEUE.add(dnsUpdateInfo);
            return updateDnsInfo(dnsUpdateInfo, sync);
        } else {
            return DnsCache.getCacheDns(domain);
        }
    }

    public static void addDnsUpdateTask(String host, Long lastUpdateTime) {

        if (IPAddressUtil.isIPv4LiteralAddress(host)) {
            return;
        }

        if (DOMAIN_FILTER.add(host.toLowerCase())) {
            DnsUpdateInfo dnsUpdateInfo = new DnsUpdateInfo();
            dnsUpdateInfo.domain = host;
            dnsUpdateInfo.createTime = null == lastUpdateTime ? -1 : lastUpdateTime;
            DOMAIN_FETCH_QUEUE.add(dnsUpdateInfo);
        }
    }

    public static InetAddress[] preFetchSync(String domain) {
        return _preFetch(domain, true);
    }

}
