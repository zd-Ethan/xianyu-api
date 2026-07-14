# xianyu-api

闲鱼助手后端 API 项目，基于 Spring Boot、MyBatis-Plus 和 SQLite 开发。

## 项目定位

本仓库只包含后端代码，前端仓库地址：

```text
https://github.com/zd-Ethan/xianyu-automation
```

后端默认监听 `12400` 端口，前端通过 `/api` 和 `/ai` 访问本服务。

## 环境要求

- JDK 21
- Windows 使用 `mvnw.cmd`
- Linux/macOS 使用 `./mvnw`

## 默认账号

本地首次启动时，如果 `sys_user` 表为空，系统会自动创建默认管理员账号：

```text
账号：admin
密码：admin
```

如果本地已有账号，系统不会覆盖现有用户。

## 本地启动

Windows 可以直接双击仓库根目录下的 `start-api.cmd`。

也可以在终端执行：

```bash
git clone https://github.com/zd-Ethan/xianyu-api.git
cd xianyu-api
.\mvnw.cmd spring-boot:run
```

启动后访问：

```text
http://localhost:12400
```

## 打包

```bash
cd xianyu-api
.\mvnw.cmd clean package -DskipTests
```

打包产物：

```text
target/XianYuAssistant-<version>.jar
```

## 运行 Jar

请务必在 `xianyu-api` 目录下执行，这样数据库和日志才会写到当前后端项目目录：

```bash
cd xianyu-api
java -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -jar target/XianYuAssistant-<version>.jar
```

## 运行数据

运行时数据默认保存在后端项目目录下：

```text
dbdata/   SQLite 数据库与数据库备份
logs/     后端运行日志
```

默认数据库文件：

```text
dbdata\xianyu_assistant.db
```

## Docker

```bash
docker build -t xianyu-api .
docker run -p 12400:12400 -v ./dbdata:/app/dbdata -v ./logs:/app/logs xianyu-api
```

## 常用命令

```bat
.\mvnw.cmd spring-boot:run              # 本地开发启动
.\mvnw.cmd clean package -DskipTests    # 打包，跳过测试
```

## 注意事项

- Windows 终端建议使用 UTF-8，避免中文日志乱码。
- 项目已在 Maven 中固定源码编译编码为 UTF-8。
- 不要提交 `dbdata/`、`logs/`、`target/` 等运行时目录。
