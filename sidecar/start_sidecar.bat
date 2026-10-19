@echo off
cd /d D:\Workspace\Agent\deepresearch\sidecar
set PYTHONIOENCODING=utf-8
uv run uvicorn main:app --host 0.0.0.0 --port 8100
