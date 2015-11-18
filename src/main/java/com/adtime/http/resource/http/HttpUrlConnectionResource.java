package com.adtime.http.resource.http;

import com.adtime.http.resource.*;
import com.adtime.http.resource.exception.DownloadStreamException;
import com.adtime.http.resource.url.URLCanonicalizer;
import com.adtime.http.resource.util.SSLSocketUtil;

import javax.annotation.PostConstruct;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocketFactory;
import java.net.*;
import java.util.List;
import java.util.Map;

/**
 * Created by Lubin.Xuan on 2015/6/2.
 * ie.
 */
public class HttpUrlConnectionResource extends WebResource {

    protected static SSLSocketFactory sslSocketFactory;

    private static final CookieManager manager;

    static {
        try {
            sslSocketFactory = SSLSocketUtil.getSslcontext().getSocketFactory();
        } catch (Exception e) {
            sslSocketFactory = null;
        }
        manager = new CookieManager();
        manager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(manager);
    }

    @Override
    protected boolean _handException(Throwable e, String url, String oUrl) {
        return e instanceof SocketException ||
                e instanceof SocketTimeoutException ||
                e instanceof UnknownHostException;
    }

    @Override
    public void registerCookie(String domain, String name, String value) {
        HttpCookie cookie = new HttpCookie(name, value);
        cookie.setDomain(domain);
        cookie.setPath("/");
        cookie.setVersion(0);
        manager.getCookieStore().add(null, cookie);
    }


    private Proxy authProxy = null;

    @PostConstruct
    public void _init() {
        if (config.getProxyHost() != null) {
            authProxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(config.getProxyHost(), config.getProxyPort()));
            if (null != config.getProxyUsername()) {
                Authenticator.setDefault(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(config.getProxyUsername(), config.getProxyPassword().toCharArray());
                    }
                });
            }
        }
    }


    @Override
    public Result request(String url, String oUrl, Request request) {
        return doRequest(url, url, config, request);
    }

    public Result doRequest(String targetUrl, String oUrl, CrawlConfig config, Request request) {
        int retryCount = config.getRetryCount();
        Result result = null;
        URL url;

        try {
            if (Request.Method.GET.equals(request.getMethod()) || Request.Method.HEAD.equals(request.getMethod())) {
                url = new URL(targetUrl);
            } else {
                url = new URL(targetUrl);
            }
        } catch (Exception e) {
            handException(e, targetUrl, oUrl);
            return new Result(targetUrl, WebConst.HTTP_ERROR, e.toString());
        }

        do {
            boolean isTimeOut = false;
            HttpURLConnection con = null;
            try {
                if (null != authProxy) {
                    con = (HttpURLConnection) url.openConnection(authProxy);
                } else {
                    con = (HttpURLConnection) url.openConnection();
                }
                con.setRequestMethod(request.getMethod().name());
                con.setDoInput(true);
                con.setUseCaches(false);
                con.setInstanceFollowRedirects(config.isFollowRedirects());
                if (null != request.getConnectionTimeout()) {
                    con.setConnectTimeout(request.getConnectionTimeout());
                } else {
                    con.setConnectTimeout(config.getConnectionTimeout());
                }
                if (null != request.getReadTimeout()) {
                    con.setReadTimeout(request.getReadTimeout());
                } else {
                    con.setReadTimeout(config.getSocketTimeout());
                }
                Map<String, String> default_headers = request.getHeaderMap();
                default_headers.forEach(con::setRequestProperty);
                if (con instanceof HttpsURLConnection) {
                    if (null == sslSocketFactory) {
                        throw new SSLException("SSLSocketFactory 没有被初始化!!!");
                    }
                    ((HttpsURLConnection) con).setSSLSocketFactory(sslSocketFactory);
                }

                if (Request.Method.POST.equals(request.getMethod()) && null != request.getRequestParam() && !request.getRequestParam().isEmpty()) {
                    con.setDoOutput(true);
                    con.getOutputStream().write(RequestUtil.buildGetParameter(request.getRequestParam()).getBytes("utf-8"));
                } else {
                    con.setDoOutput(false);
                    con.connect();
                }
                int sts = con.getResponseCode();

                Map<String, List<String>> headerMap = con.getHeaderFields();

                if (sts == HttpURLConnection.HTTP_MOVED_PERM || sts == HttpURLConnection.HTTP_MOVED_TEMP) {
                    return handleRedirect(con, targetUrl).withHeader(headerMap);
                } else {
                    if (Request.Method.HEAD.equals(request.getMethod())) {
                        return new Result(targetUrl, sts, "").withHeader(headerMap);
                    } else {
                        if (sts == HttpURLConnection.HTTP_OK) {
                            return handleSuccess(con, request.getCharSet(), targetUrl, request.isCheckBodySize()).withHeader(headerMap);
                        } else if (sts >= 400) {
                            return handleError(con, request.getCharSet(), targetUrl).withHeader(headerMap);
                        } else {
                            return new Result(targetUrl, "", false, sts).withHeader(headerMap);
                        }
                    }
                }
            } catch (Exception e) {
                if (e instanceof DownloadStreamException) {
                    return new Result(targetUrl, WebConst.DOWNLOAD_STREAM, e.toString());
                }
                isTimeOut = handException(e, targetUrl, oUrl);
                result = new Result(targetUrl, WebConst.HTTP_ERROR, e.toString());
            } finally {
                closeHttpURLConnection(con);
            }
            if (!isTimeOut) {
                break;
            }
            retryCount--;
        } while (retryCount > 0);
        url = null;
        return result;
    }


    private void closeHttpURLConnection(HttpURLConnection con) {
        if (null != con) {
            try {
                if (null != con.getInputStream()) {
                    con.getInputStream().close();
                }
            } catch (Exception ignore) {
            }

            try {
                if (null != con.getErrorStream()) {
                    con.getErrorStream().close();
                }
            } catch (Exception ignore) {
            }

            try {
                if (null != con.getOutputStream()) {
                    con.getOutputStream().close();
                }
            } catch (Exception ignore) {
            }

            con.disconnect();
        }
    }


    private Result handleError(HttpURLConnection con, String charSet, String url) throws Exception {
        return getResult(con, true, charSet, url, true);
    }

    private Result getResult(HttpURLConnection con, boolean error, String charSet, String url, boolean checkBodySize) throws Exception {
        String header_contentType = con.getContentType();
        if (null != header_contentType) {
            header_contentType = header_contentType.replaceAll("\\s", "");
        }
        String contentType = null;
        String contentCharset = null;
        String charsetKey = ";charset=";
        if (null != header_contentType) {
            if (header_contentType.toLowerCase().contains(charsetKey)) {
                int idx_pre = header_contentType.toLowerCase().indexOf(charsetKey);
                contentType = header_contentType.substring(0, idx_pre);
                contentCharset = header_contentType.substring(idx_pre + +charsetKey.length());
            } else {
                contentType = header_contentType;
            }
        }
        if (null != contentCharset) {
            charSet = contentCharset;
        }

        EntityReadUtils.Entity entity = EntityReadUtils.read(con, error, charSet, checkBodySize);
        String content = entity.toString(url);
        Result tmp = new Result(url, content, false, con.getResponseCode())
                .setContentType(contentType).setCharSet(entity.getFinalCharSet());
        copy(entity, tmp);
        entity = null;
        return tmp;
    }

    private Result handleSuccess(HttpURLConnection con, String charSet, String url, boolean checkBodySize) throws Exception {
        Result tmp = getResult(con, false, charSet, url, checkBodySize);
        String refreshUrl = metaRefresh(tmp);
        if (refreshUrl.trim().length() > 0) {
            Result result = new Result(url, "", true, HttpURLConnection.HTTP_MOVED_PERM);
            result.setMoveToUrl(URLCanonicalizer.mergePathUrl(url, refreshUrl));
            return result;
        }
        return tmp;
    }

    private Result handleRedirect(HttpURLConnection con, String url) throws Exception {
        Result result = new Result(url, "", true, con.getResponseCode());
        String reLocation = con.getHeaderField("Location");
        if (reLocation != null) {
            String movedToUrl = URLCanonicalizer.mergePathUrl(url, reLocation);
            result.setMoveToUrl(movedToUrl);
        }
        return result;
    }
}