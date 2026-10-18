# KEFA 选股平台 — Docker 生产部署指南

本文档记录将 KEFA（React + FastAPI + PostgreSQL）部署到腾讯云 / 阿里云等 Linux 服务器的流程。  
基于 **2026-08-02** 在 Ubuntu 20.04（`106.54.61.165`，用户 `ubuntu`，域名 `kalaxiaoke.cloud`）上的实际部署整理。

---

## 1. 部署架构

```
  浏览器
     │
     ▼
  Caddy :80/:443  ──自动 HTTPS (Let's Encrypt)──┐
     │                                            │
     ▼                                            │
  web (Nginx) :80  ──静态前端── /api 反代──► api :8000 (FastAPI)
                                                 │
                                                 ▼
                                            db :5432 (Postgres 16)
```

| 容器 | 说明 | 宿主机端口 |
|------|------|------------|
| `caddy` | TLS 终结 + 反代到 web | **80 / 443** |
| `web` | 前端静态资源 + `/api` 反代 | **8080**（备用直连） |
| `api` | FastAPI 后端 | 仅内网 |
| `db` | PostgreSQL 16 | 仅内网 |

访问入口：

| 地址 | 说明 |
|------|------|
| http://106.54.61.165 | **未备案时推荐**（走 80，已验证可用） |
| http://kalaxiaoke.cloud | 域名（需备案；备案前可能被拦） |
| https://kalaxiaoke.cloud | HTTPS（需安全组放行 443） |
| http://106.54.61.165:8080 | 需安全组额外放行 8080，一般不必开 |

---

## 2. 环境要求

### 服务器

- Ubuntu 20.04 / 22.04 / 24.04
- 建议 ≥ 2 核、4 GB 内存、40 GB 磁盘（本次实测约 3.6 GB 内存可运行）
- 安全组 / 防火墙放行：**22、80、443**（可选 **8080**）

### 本机

- SSH 私钥（本次：`C:\Users\Administrator\.ssh\kefa.pem`）
- 登录用户：**`ubuntu`**（不是 root；root 未授权该密钥）

```powershell
ssh -i $env:USERPROFILE\.ssh\kefa.pem ubuntu@106.54.61.165
```

可选 `~/.ssh/config`：

```
Host kefa
    HostName 106.54.61.165
    User ubuntu
    IdentityFile ~/.ssh/kefa.pem
```

---

## 3. 域名与 HTTPS（必做）

当前证书申请失败原因（实测）：

1. **`kalaxiaoke.cloud` 根域名没有 A 记录**（Let's Encrypt：`no valid A records`）
2. **`www.kalaxiaoke.cloud` 指向 Cloudflare 代理 IP**，挑战打到 CF 返回 502，到不了本机

### 3.1 在 Cloudflare DNS 中配置

| 类型 | 名称 | 内容 | 代理状态 |
|------|------|------|----------|
| A | `@` | `106.54.61.165` | **仅 DNS（灰云）** |
| A | `www` | `106.54.61.165` | **仅 DNS（灰云）** |

签发阶段务必关橙色云，否则 HTTP-01 校验容易失败。

### 3.2 等待解析生效后

```bash
# 本机或服务器上检查
dig +short kalaxiaoke.cloud A
# 应输出 106.54.61.165

cd /opt/kefa
docker compose restart caddy
docker compose logs -f caddy
# 看到 certificate obtained successfully 即成功
```

证书成功后，可按需打开 Cloudflare 橙色云，SSL/TLS 模式选 **Full (strict)**。

### 3.3 腾讯云安全组

放行入站：TCP 80、443、8080（以及 22）。

---

## 4. 安装 Docker（国内源）

官方 `get.docker.com` 在国内常被重置，改用阿里云 Docker CE：

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg lsb-release apt-transport-https
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://mirrors.aliyun.com/docker-ce/linux/ubuntu/gpg \
  | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://mirrors.aliyun.com/docker-ce/linux/ubuntu $(lsb_release -cs) stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io \
  docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker ubuntu
# 重新登录 SSH 使 docker 组生效，或：
newgrp docker
```

### 4.1 镜像加速

```bash
sudo mkdir -p /etc/docker
sudo cp /opt/kefa/deploy/daemon.json /etc/docker/daemon.json
# 或手写：
# {"registry-mirrors":["https://docker.1panel.live","https://docker.m.daocloud.io","https://docker.1ms.run"]}
sudo systemctl restart docker
docker info | grep -A6 "Registry Mirrors"
```

> 注意：`daemon.json` 必须是合法 JSON。若写成 PowerShell 展开后的非 JSON，Docker 会无法启动。

---

## 5. 上传代码

部署目录：`/opt/kefa`

### 本机打包上传（Windows）

在项目根目录：

```powershell
tar -czf kefa-deploy.tgz `
  --exclude=node_modules --exclude=.venv --exclude=frontend/dist `
  --exclude=.git --exclude=*.log --exclude=kefa-deploy.tgz `
  --exclude=backend/.env --exclude=.env --exclude=backend/proxies.txt `
  .

scp -i $env:USERPROFILE\.ssh\kefa.pem kefa-deploy.tgz ubuntu@106.54.61.165:/tmp/
ssh -i $env:USERPROFILE\.ssh\kefa.pem ubuntu@106.54.61.165 `
  "sudo mkdir -p /opt/kefa && sudo chown -R ubuntu:ubuntu /opt/kefa && cd /opt/kefa && tar -xzf /tmp/kefa-deploy.tgz && rm /tmp/kefa-deploy.tgz"
```

也可使用仓库内脚本（上传后）：

```bash
bash /tmp/remote-bootstrap.sh   # 见 deploy/remote-bootstrap.sh
```

---

## 6. 环境变量

```bash
cd /opt/kefa
cp .env.docker.example .env
nano .env
```

必改：

```env
POSTGRES_PASSWORD=强密码
CORS_ORIGINS=https://kalaxiaoke.cloud,https://www.kalaxiaoke.cloud
```

`.env` 已在 `.gitignore`，不要提交。

---

## 7. 启动

```bash
cd /opt/kefa
docker compose up -d --build
docker compose ps
```

健康检查：

```bash
docker exec kefa-web-1 wget -qO- http://api:8000/api/meta/health
# {"success":true,...,"status":"ok"}
```

临时用 IP 打开前端：

```text
http://106.54.61.165:8080/
```

---

## 8. 日常运维

```bash
cd /opt/kefa

docker compose ps
docker compose logs -f api
docker compose logs -f caddy
docker compose restart api
docker compose up -d --build      # 更新代码后重建
docker compose down               # 停止（保留数据卷）
```

### 备份数据库

```bash
docker compose exec -T db pg_dump -U kefa kefa | gzip > kefa-$(date +%F).sql.gz
```

### 同步行情数据（服务器上）

容器内执行脚本（需外网；Baostock 建议配代理）：

```bash
docker compose exec api python scripts/init_baostock_data.py --boards main --workers 1
```

---

## 9. 相关文件

| 文件 | 说明 |
|------|------|
| `docker-compose.yml` | 编排：db / api / web / caddy |
| `.env.docker.example` | 环境变量模板 |
| `deploy/Caddyfile` | 域名 + HTTPS + IP HTTP |
| `deploy/daemon.json` | Docker 镜像加速 |
| `deploy/remote-bootstrap.sh` | 服务器解压/生成密码/启动 |
| `backend/Dockerfile` | 后端镜像（阿里云 pip/apt） |
| `frontend/Dockerfile` | 前端构建 + Nginx |
| `frontend/nginx.conf` | `/api` 反代到 api:8000 |

---

## 10. 常见问题

### Q1：证书一直失败

- 检查 `dig +short kalaxiaoke.cloud` 是否为 `106.54.61.165`
- Cloudflare 先改灰云
- 安全组放行 80
- `docker compose logs caddy` 看 ACME 报错

### Q2：拉取镜像超时

配置 `deploy/daemon.json` 后 `sudo systemctl restart docker`。

### Q3：`Permission denied (publickey)` 用 root

改用：`ssh -i kefa.pem ubuntu@106.54.61.165`。

### Q4：Docker 起不来，daemon.json invalid character

用项目里的 `deploy/daemon.json` 覆盖 `/etc/docker/daemon.json`，勿用错误转义写入。

### Q5：页面能开但 API 502

```bash
docker compose logs api
docker compose ps
docker exec kefa-web-1 wget -qO- http://api:8000/api/meta/health
```

---

## 11. 部署检查清单

- [x] SSH：`ubuntu` + `kefa.pem` 可登录
- [x] Docker CE + Compose（阿里云源）已安装
- [x] 镜像加速 `daemon.json` 已配置
- [x] `/opt/kefa` 代码已上传，`docker compose up -d --build` 成功
- [x] API 健康检查 OK
- [x] 临时入口 `http://106.54.61.165:8080` 可用
- [x] DNS：`@` / `www` A 记录 → `106.54.61.165`
- [x] Caddy 成功签发 `kalaxiaoke.cloud` / `www` 证书
- [ ] 云安全组放行 **443**（若外网 HTTPS 超时，优先查此项）
- [ ] https://kalaxiaoke.cloud 外网可访问
- [ ] （可选）初始化 Baostock 行情/财务数据

---

## 12. 本次服务器信息（备忘）

| 项 | 值 |
|----|-----|
| IP | `106.54.61.165` |
| 用户 | `ubuntu` |
| 密钥 | `~/.ssh/kefa.pem` |
| 部署目录 | `/opt/kefa` |
| 域名 | `kalaxiaoke.cloud` / `www.kalaxiaoke.cloud` |
