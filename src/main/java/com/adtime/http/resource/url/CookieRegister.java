package com.adtime.http.resource.url;

import com.adtime.http.resource.WebResource;
import com.adtime.http.resource.common.ConfigReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * Created by Lubin.Xuan on 2015/6/12.
 * ie.
 */
public class CookieRegister {

    private static final Logger logger = LoggerFactory.getLogger(CookieRegister.class);

    private List<WebResource> webResourceList;

    private String cookieConf;

    @PostConstruct
    public void init() {
        try {
            String data = ConfigReader.readNOE(cookieConf);
            String[] lines = data.split("\n");
            Set<String> cookie = new HashSet<>();
            for (String line : lines) {
                String[] ck = line.split("\\\\t");
                if (ck.length == 3 && cookie.add(ck[1] + "@" + ck[0])) {
                    registerCookie(ck[1], ck[0], ck[2]);
                }
            }
        } catch (Exception e) {
            logger.error("非法url初始化出错", e);
        }
    }


    public void setCookieConf(String cookieConf) {
        this.cookieConf = cookieConf;
    }

    public void setWebResourceList(List<WebResource> webResourceList) {
        this.webResourceList = webResourceList;
    }


    public void registerCookie(String domain, String name, String value) {
        if (null == webResourceList || webResourceList.isEmpty()) {
            return;
        }
        webResourceList.forEach(w -> w.registerCookie(domain, name, value));
    }
}