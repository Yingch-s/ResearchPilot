FROM python:3.12-slim
WORKDIR /app
ENV PYTHONUNBUFFERED=1 HF_HOME=/root/.cache/huggingface
COPY pyproject.toml uv.lock ./
RUN pip install --no-cache-dir --upgrade pip && pip install --no-cache-dir .
COPY main.py ./
EXPOSE 8100
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8100"]
