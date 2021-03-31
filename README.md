# update-domain-record-ip

update-domain-record-ip 是一个同步域名解析到外网 ip 的工具，域名必须是在阿里云托管，且路由器必须是 netgear

## 原理

通过调用阿里云修改解析 api 实现，
获取公网 ip 的是通过爬取路由器页面实现，目前只实现了爬取 netgear 的页面。

## 编译

```bash
mvn assembly:assembly
```

## Usage

修改 www.example.com 和 blog.example.com 的域名解析
```bash
java -jar target/updateDomainRecordIp-1.0-SNAPSHOT-jar-with-dependencies.jar \
-d example.com \
-rid 1234 \
-rn www \
-rid 5678
-rn blog \
--access-key-id 9fan39ApGDHiHNhz \
--access-key-secret kVBrq1nDHEUia6EPHkOAtNloADI1Jt \
--signature-method HMAC-SHA1
```

## 参数列表
|参数名称|描述|
|--------|----|
|-d|域名|
|-rid|record id，可以传多个|
|-rn|record name，即子域名，可以传多个，必须与 rid 一一对应|
|--access-key-id|aliyun access key id|
|--access-key-secret|aliyun access key secret|
|--signature-method|aliyun signature method|
