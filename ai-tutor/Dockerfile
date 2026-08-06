FROM python:3.12-slim
WORKDIR /app

# 国内服务器使用阿里云 apt / PyPI 镜像，避免 Debian / PyPI 拉取超时
RUN sed -i 's|deb.debian.org|mirrors.aliyun.com|g; s|security.debian.org|mirrors.aliyun.com|g' \
        /etc/apt/sources.list.d/debian.sources 2>/dev/null \
    || sed -i 's|deb.debian.org|mirrors.aliyun.com|g; s|security.debian.org|mirrors.aliyun.com|g' \
        /etc/apt/sources.list 2>/dev/null \
    || true \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
        build-essential \
    && rm -rf /var/lib/apt/lists/*

# Python 依赖（Docker 层缓存）
COPY requirements.txt requirements-unstructured.txt ./
RUN pip install --no-cache-dir --index-url https://mirrors.aliyun.com/pypi/simple/ -r requirements.txt

# 可选：复杂 PDF/DOCX 结构解析
ARG INSTALL_UNSTRUCTURED=false
RUN if [ "$INSTALL_UNSTRUCTURED" = "true" ]; then pip install --no-cache-dir --index-url https://mirrors.aliyun.com/pypi/simple/ -r requirements-unstructured.txt; fi

# 源码
COPY . .

# ChromaDB 数据 + 会话库（挂载点）
RUN mkdir -p /app/chroma_store

EXPOSE 8080

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8080", "--no-access-log"]
