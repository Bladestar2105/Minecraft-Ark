FROM python:3.14-slim@sha256:cd89c30eda26ee911a4611b18680086e0ea42e6f5f8cbe653d07fa7958c9cec8
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
