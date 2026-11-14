FROM python:3.15-slim@sha256:65a65fc7b30b06cb6df4930fd4650697cb23f4c2698ab81fc87e4b031bc36928
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app.py catalog.py config_content.py rcon_client.py player_app.py wardrobe_client.py i18n.py rate_limit.py ./
COPY templates ./templates
COPY static ./static
USER 1000:1000
EXPOSE 8090
CMD ["gunicorn", "--no-control-socket", "--bind", "0.0.0.0:8090", "--workers", "1", "--threads", "16", "--timeout", "150", "app:create_app()"]
