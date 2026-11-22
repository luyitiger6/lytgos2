# 认证（1.0 边界）

LytgOS2 1.0 **没有认证、授权、RBAC、SSO、API Key 或 CORS 白名单**。它是面向可信内网的单机运行时内核。

## 这意味着什么

- 所有 REST 端点都不需要认证。
- CORS 全开。
- 任何能访问 `8080` 端口的人都可以调用 Agent、读取 Session。
- 不要把服务直接暴露到公网。

## 推荐部署方式

- 放在反向代理或 VPN 后面。
- 绑定到内网接口，或用防火墙/安全组限制端口。
- 使用网络层访问控制（VPC、ingress allowlist、代理层 mTLS）。
- Provider 密钥放在环境变量或外部配置文件中，绝不提交到仓库。

## 扩展方向

认证（OIDC）、RBAC、API Key、CORS 白名单、多租户和 SSO 属于扩展阶段能力，1.0 不会以半成品形式提供。

## 相关

- [REST API](/zh/docs/api)
- [路线图](/zh/docs/roadmap)
