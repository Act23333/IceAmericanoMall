FROM nginx:1.27-alpine

# 清理默认配置
RUN rm -rf /etc/nginx/conf.d/default.conf

# 复制自定义配置
COPY conf.d/gateway.conf /etc/nginx/conf.d/gateway.conf
COPY conf.d/frontend.conf /etc/nginx/conf.d/frontend.conf

# 创建缓存目录
RUN mkdir -p /var/cache/nginx && chown -R nginx:nginx /var/cache/nginx

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD nginx -t || exit 1

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
