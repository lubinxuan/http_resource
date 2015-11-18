package com.adtime.http.resource;

import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

import java.util.List;
import java.util.Map;

/**
 * Created by Administrator on 2014/8/25.
 */
public class Result {
    private String url;
    private String moveToUrl;
    private String html;
    private boolean redirect;
    private int redirectCount;
    private int status;
    private String contentType;
    private String charSet;
    private Document document;

    private Map<String, List<String>> headersMap;

    private String message;

    protected long length;
    protected long unCompressLength;
    protected long requestTime;

    private boolean parsed;

    public Result(String url, String html, boolean redirect, int status) {
        this.url = url;
        this.html = html;
        this.redirect = redirect;
        this.status = status;
    }


    public Result(String url, int status, String message) {
        this.url = url;
        this.status = status;
        this.message = message;
        this.redirect = false;
    }

    public String getContentType() {
        return contentType;
    }

    public Result setContentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    public int getRedirectCount() {
        return redirectCount;
    }

    protected void setRedirectCount(int redirectCount) {
        this.redirectCount = redirectCount;
    }

    public int getStatus() {
        return status;
    }

    protected void setUrl(String url) {
        this.url = url;
    }

    protected void setRedirect(boolean redirect) {
        this.redirect = redirect;
    }

    public String getUrl() {
        return url;
    }

    public String getHtml() {
        return html;
    }

    public boolean isRedirect() {
        return redirect;
    }

    public String getMoveToUrl() {
        return moveToUrl;
    }

    public void setMoveToUrl(String moveToUrl) {
        this.moveToUrl = moveToUrl;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Document getDocument() {
        return getDocument(false);
    }

    public Document getDocument(boolean removeStyle) {
        return getDocument(removeStyle, false);
    }

    public Document getDocument(boolean removeStyle, boolean asXml) {
        if (!parsed) {
            synchronized (this) {
                if (!parsed) {
                    if (null != html && html.length() > 0) {
                        try {
                            if (asXml) {
                                document = Parser.xmlParser().parseInput(html, "");
                            } else {
                                document = Parser.htmlParser().parseInput(html, "");
                                if (removeStyle) {
                                    document.select("style").remove();
                                    document.select("link").remove();
                                }
                            }
                        } catch (Exception ignore) {

                        }
                    }
                    parsed = true;
                }
            }
        }
        return document;
    }

    public String getCharSet() {
        return charSet;
    }

    public Result setCharSet(String charSet) {
        this.charSet = charSet;
        return this;
    }

    public Map<String, List<String>> getHeadersMap() {
        return headersMap;
    }

    public long getLength() {
        return length;
    }

    public long getUnCompressLength() {
        return unCompressLength;
    }

    public long getRequestTime() {
        return requestTime;
    }

    public void setRequestTime(long requestTime) {
        this.requestTime = requestTime;
    }

    public Result withHeader(Map<String, List<String>> header) {
        this.headersMap = header;
        return this;
    }

    @Override
    public String toString() {
        return "Result{" +
                "url='" + url + '\'' +
                ", moveToUrl='" + moveToUrl + '\'' +
                ", html='" + html + '\'' +
                ", redirect=" + redirect +
                ", status=" + status +
                ", message='" + message + '\'' +
                '}';
    }
}