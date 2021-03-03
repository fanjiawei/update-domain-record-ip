package jevi;

import org.apache.commons.cli.*;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.helper.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class App {
    private static final Logger log = LoggerFactory.getLogger(App.class);
    private static String AccessKeyId = "";
    private static String AccessKeySecret = "";
    private static String SignatureMethod = "";

    public static void main(
            String[] args) throws IOException, NoSuchAlgorithmException, InvalidKeyException, ParseException {
        Options options = new Options();
        options.addOption(Option.builder("d").longOpt("domain").hasArg().desc("要处理的域名").build());
        options.addOption(Option.builder("rid").longOpt("record-id").hasArg().desc("被解析的Record id").build());
        options.addOption(Option.builder("rn").longOpt("record-name").hasArg().desc("被解析的Record name").build());
        options.addOption(Option.builder().longOpt("netgear-username").desc("netgear管理员账号").hasArg().build());
        options.addOption(Option.builder().longOpt("netgear-password").desc("netgear管理员密码").hasArg().build());
        options.addOption(Option.builder().longOpt("access-key-id").hasArg().build());
        options.addOption(Option.builder().longOpt("access-key-secret").hasArg().build());
        options.addOption(Option.builder().longOpt("signature-method").hasArg().build());

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        log.debug(Arrays.toString(cmd.getOptionValues("domain")));
        log.debug(cmd.getOptionValue("netgear-username"));
        log.debug(cmd.getOptionValue("netgear-password"));

        App app = new App();
        String publicIp = new NetGearRouterIpProvider(cmd.getOptionValue("netgear-username").trim(),
                cmd.getOptionValue("netgear-password").trim()
        )
                .getIP()
                .getHostAddress();

        log.debug("外网IP：" + publicIp);
        log.debug("时间戳：" + app.getTimestamp());

        App.AccessKeyId = cmd.getOptionValue("access-key-id");
        App.AccessKeySecret = cmd.getOptionValue("access-key-secret");
        App.SignatureMethod = cmd.getOptionValue("signature-method");

        String domain = cmd.getOptionValue("domain");
        String recordId = cmd.getOptionValue("record-id");
        String recordName = cmd.getOptionValue("record-name");
        log.debug("access-key-id=" + App.AccessKeyId);
        log.debug("access-key-secret=" + App.AccessKeySecret);
        log.debug("signature-method=" + App.SignatureMethod);
        log.debug("domain=" + domain);
        log.debug("record-id=" + recordId);
        log.debug("record-name=" + recordName);


        if (app.publicIpIsChange(publicIp, recordName + '.' + domain)) {
            app.updateDomainRecord(domain, recordId, recordName, publicIp);
        } else {
            log.debug("IP地址未变，不必修改，" + publicIp);
        }
    }

    public Boolean publicIpIsChange(String publicIp, String domain) throws UnknownHostException {
        if (StringUtil.isBlank(publicIp)) {
            return false;
        }

        InetAddress address = InetAddress.getByName(domain);
        return !address.getHostAddress().equals(publicIp);
    }


    public String getTimestamp() {
        return ZonedDateTime.now().withZoneSameInstant(ZoneId.of("Z")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static final String ENCODING = "UTF-8";

    private String percentEncode(String value) throws UnsupportedEncodingException {
        return value != null ? URLEncoder
                .encode(value, ENCODING)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~") : null;
    }

    public String getQueryString(Map<String, String> parameters) throws UnsupportedEncodingException {
        // 对参数进行排序，注意严格区分大小写
        String[] sortedKeys = parameters.keySet().toArray(new String[]{});
        StringBuilder canonicalizedQueryString = new StringBuilder();
        Arrays.sort(sortedKeys);
        for (String key : sortedKeys) {
            // 这里注意对key和value进行编码
            canonicalizedQueryString
                    .append("&")
                    .append(percentEncode(key))
                    .append("=")
                    .append(percentEncode(parameters.get(key)));
        }
        return canonicalizedQueryString.toString().substring(1);
    }

    public String getSignature(
            Map<String, String> parameters) throws NoSuchAlgorithmException, UnsupportedEncodingException, InvalidKeyException {
        StringBuilder stringToSign = new StringBuilder();
        stringToSign.append("GET").append("&").append(percentEncode("/")).append("&");
        stringToSign.append(percentEncode(getQueryString(parameters)));

        final String ALGORITHM = "HmacSHA1";
        final String ENCODING = "UTF-8";
        String key = AccessKeySecret + "&";
        Mac mac = Mac.getInstance(ALGORITHM);
        mac.init(new SecretKeySpec(key.getBytes(ENCODING), ALGORITHM));
        byte[] signData = mac.doFinal(stringToSign.toString().getBytes(ENCODING));
        String signature = new String(Base64.getEncoder().encode(signData));
        return signature;
    }

    public void handleDomainRecord(
            Map<String, String> params) throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("AccessKeyId", AccessKeyId);
        parameters.put("SignatureMethod", SignatureMethod);
        parameters.put("SignatureNonce", UUID.randomUUID().toString());
        parameters.put("SignatureVersion", "1.0");
        parameters.put("Timestamp", getTimestamp());
        parameters.put("Version", "2015-01-09");
        parameters.put("Format", "json");

        params.forEach((key, value) -> {
            parameters.put(key, value);
        });

        CloseableHttpClient httpClient = HttpClients.createDefault();

        String queryString = getQueryString(parameters);
        queryString = queryString + "&Signature=" + percentEncode(this.getSignature(parameters));
        HttpGet httpGet = new HttpGet("http://alidns.aliyuncs.com?" + queryString);

        CloseableHttpResponse response = httpClient.execute(httpGet);
        System.out.println(EntityUtils.toString(response.getEntity()));

        httpClient.close();
    }

    public void addDomainRecord(
            String domainName, String rr,
            String value) throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("Action", "AddDomainRecord");
        parameters.put("DomainName", domainName);
        parameters.put("RR", rr);
        parameters.put("Type", "A");
        parameters.put("Value", value);

        handleDomainRecord(parameters);
    }

    public void updateDomainRecord(
            String domainName, String recordId, String rr,
            String value) throws NoSuchAlgorithmException, InvalidKeyException, IOException {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("Action", "UpdateDomainRecord");
        parameters.put("RecordId", recordId);
        parameters.put("DomainName", domainName);
        parameters.put("RR", rr);
        parameters.put("Type", "A");
        parameters.put("Value", value);

        handleDomainRecord(parameters);
    }

    public void listDomainRecords(String domainName) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("Action", "DescribeDomainRecords");
        parameters.put("DomainName", domainName);

        handleDomainRecord(parameters);
    }
}
