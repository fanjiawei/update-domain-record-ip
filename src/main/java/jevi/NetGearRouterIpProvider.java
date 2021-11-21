package jevi;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.internal.StringUtil;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.util.Base64;

/**
 * 从 netgear 配置页面获取ip，
 * 原理为程序登录 netgear 路由器，爬取含有外网 ip 的页面
 */
public class NetGearRouterIpProvider implements PublicIpProvider {
    private static Logger log = LoggerFactory.getLogger(NetGearRouterIpProvider.class);
    private String username;
    private String password;
    private final String PUBLIC_IP_PAGE = "http://10.0.0.1/BAS_pppoe.htm";
    private String cookie;

    public NetGearRouterIpProvider(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public InetAddress getIP() {
        Connection connection = Jsoup.connect(PUBLIC_IP_PAGE);
        connection.ignoreHttpErrors(true);

        try {
            String auth = new String(Base64.getEncoder()
                    .encode((this.getUsername() + ":" + this.getPassword()).getBytes("UTF-8")));

            connection.header("Authorization", "Basic " + auth);
            if (!StringUtil.isBlank(cookie)) {
                connection.header("cookie", this.cookie);
            }

            int responseCode = connection.execute().statusCode();
            if (responseCode == 401) {
                log.debug("获取 cookie");
                this.cookie = connection.execute().header("Set-Cookie");
                return this.getIP();
            }
            Document doc = connection.get();
            log.debug(doc.html());
            Elements elements = doc.select("input[name=wan_ipaddr]");
            if (elements.size() == 0) {
                throw new RuntimeException("获取公网 ip 失败");
            }
            return InetAddress.getByName(elements.attr("value"));
        } catch (UnsupportedEncodingException e) {
            log.debug(e.getMessage());
            return null;
        } catch (IOException e) {
            log.debug(e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }


    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
