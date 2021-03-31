package jevi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.util.*;

public class HTTPIpProvider implements PublicIpProvider {
    private static final Logger log = LoggerFactory.getLogger(App.class);

    private List<String> urls = new ArrayList<>();

    public HTTPIpProvider() {
        urls.addAll(List.of("http://checkip.amazonaws.com", "http://bot.whatismyipaddress.com/",
                "http://www.trackip.net/ip", "http://icanhazip.com", "https://ipv4.icanhazip.com",
                "http://ipinfo.io/ip", "https://api.ip.sb/ip", "https://ifconfig.co/ip"
                           ));
    }

    @Override
    public InetAddress getIP() {
        Collections.shuffle(urls);
        for (int i = 0; i < urls.size(); i++) {
            URL url = null;
            try {
                log.debug("从{}获取 ip", urls.get(i));
                url = new URL(urls.get(i));
                URLConnection connection = url.openConnection();
                connection.addRequestProperty("Protocol", "Http/1.1");
                connection.addRequestProperty("Connection", "keep-alive");
                connection.addRequestProperty("Keep-Alive", "1000");
                connection.addRequestProperty("User-Agent", "Web-Agent");

                Scanner s = new Scanner(connection.getInputStream());
                try {
                    return InetAddress.getByName(s.nextLine());
                } finally {
                    s.close();
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return null;
    }
}
