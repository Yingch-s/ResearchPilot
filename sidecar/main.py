"""ResearchPilot Python sidecar：两个能力，一个进程。

  1. POST /parse          —— Docling 论文解析：PDF -> NormalizedDocument（页码锚点/章节/表格/质量标记）
  2. POST /v1/embeddings  —— bge-m3 本地 embedding，OpenAI 兼容协议（Spring AI openai starter 直连）

选型记录（2026-10-01）：embedding 走本地 bge-m3 而非 API —— 免费、离线、评测批量跑无成本，
多语言学术语料表现强；Docling 必须运行在 Python，与 Java 主服务以 HTTP 契约交互。

运行：uv run uvicorn main:app --host 0.0.0.0 --port 8100
"""

from __future__ import annotations

import importlib.metadata
import io
import logging
from pathlib import Path

import numpy as np
from docling.document_converter import DocumentConverter
from fastapi import FastAPI, HTTPException, UploadFile
from pydantic import BaseModel, Field

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(levelname)s %(message)s")
log = logging.getLogger("sidecar")

PARSER_VERSION = f"docling/{importlib.metadata.version('docling')}"
EMBED_MODEL_NAME = "BAAI/bge-m3"
EMBED_DIM = 1024

app = FastAPI(title="ResearchPilot Sidecar", version="0.1.0")

# ---- 惰性单例：Docling 与 bge-m3 模型都很重，进程内只加载一次 ----
_converter: DocumentConverter | None = None
_embed_model = None


def get_converter() -> DocumentConverter:
    global _converter
    if _converter is None:
        _converter = DocumentConverter()
    return _converter


def get_embed_model():
    global _embed_model
    if _embed_model is None:
        from sentence_transformers import SentenceTransformer

        log.info("加载 bge-m3 模型（首次约 2GB 下载）...")
        _embed_model = SentenceTransformer(EMBED_MODEL_NAME)
        log.info("bge-m3 就绪")
    return _embed_model


@app.get("/embedding-identity")
def embedding_identity():
    model = get_embed_model()
    revision = getattr(model[0].auto_model.config, "_commit_hash", None)
    if not revision:
        raise HTTPException(status_code=503, detail="Model revision unavailable; persistent cache disabled")
    return {
        "model": EMBED_MODEL_NAME,
        "revision": revision,
        "dimension": EMBED_DIM,
        "normalization": "l2",
        "runtime": importlib.metadata.version("sentence-transformers"),
    }


# ---------- NormalizedDocument 契约（与 ResearchPilot 设计文档 §3.2 对齐） ----------

class SourceBlock(BaseModel):
    block_id: str
    content_type: str
    page: int | None = None
    section_path: str | None = None
    text: str | None = None
    n_rows: int | None = None
    n_cols: int | None = None


class NormalizedDocument(BaseModel):
    document_version_id: str = ""
    source_object_key: str = ""
    parser_version: str = PARSER_VERSION
    blocks: list[SourceBlock] = Field(default_factory=list)
    quality_flags: list[str] = Field(default_factory=list)


# ---------- 1. 解析端点 ----------

@app.post("/parse", response_model=NormalizedDocument)
async def parse_pdf(file: UploadFile, document_version_id: str = "", source_object_key: str = "") -> NormalizedDocument:
    data = await file.read()
    if not data.startswith(b"%PDF"):
        raise HTTPException(415, "不是有效的 PDF 文件")

    tmp = Path(f"tmp_{document_version_id or 'upload'}.pdf")
    tmp.write_bytes(data)
    try:
        result = get_converter().convert(str(tmp))
    except Exception as e:
        raise HTTPException(500, f"Docling 解析失败: {e}") from e
    finally:
        tmp.unlink(missing_ok=True)

    doc = result.document
    blocks: list[SourceBlock] = []
    quality_flags: list[str] = []
    section_stack: list[str] = []

    for item, level in doc.iterate_items():
        kind = type(item).__name__
        page = min((p.page_no for p in item.prov), default=None) if item.prov else None
        section_path = " > ".join(section_stack) or None

        if kind == "SectionHeaderItem":
            while section_stack and len(section_stack) >= level:
                section_stack.pop()
            section_stack.append(item.text or "")
            section_path = " > ".join(section_stack)
            blocks.append(SourceBlock(
                block_id=f"SB-{len(blocks) + 1:04d}", content_type="heading",
                page=page, section_path=section_path, text=item.text,
            ))
            continue

        content_type = {
            "TextItem": "text", "ListItem": "list", "TableItem": "table",
            "PictureItem": "picture", "FormulaItem": "formula",
        }.get(kind, "other")

        entry = SourceBlock(
            block_id=f"SB-{len(blocks) + 1:04d}", content_type=content_type,
            page=page, section_path=section_path,
            text=(getattr(item, "text", None) or None) if kind != "TableItem" else None,
        )
        if kind == "TableItem":
            try:
                df = item.export_to_dataframe()
                entry.n_rows, entry.n_cols = df.shape
                # ResearchPilot B1：表格文本导出（检索与摄取切分的原料）
                entry.text = df.to_string(index=False)[:2000]
            except Exception:
                quality_flags.append(f"TABLE_UNREADABLE:{entry.block_id}")
        if page is None:
            quality_flags.append(f"BLOCK_WITHOUT_PAGE:{entry.block_id}")
        blocks.append(entry)

    if not blocks:
        quality_flags.append("EMPTY_DOCUMENT")

    normalized = NormalizedDocument(
        document_version_id=document_version_id,
        source_object_key=source_object_key,
        blocks=blocks,
        quality_flags=quality_flags,
    )
    log.info("解析完成: %d blocks, %d flags", len(blocks), len(quality_flags))
    return normalized


# ---------- 2. Embedding 端点（OpenAI 兼容） ----------

class EmbeddingRequest(BaseModel):
    input: str | list[str]
    model: str = EMBED_MODEL_NAME


class EmbeddingData(BaseModel):
    object: str = "embedding"
    index: int
    embedding: list[float]


class EmbeddingResponse(BaseModel):
    object: str = "list"
    data: list[EmbeddingData]
    model: str = EMBED_MODEL_NAME
    usage: dict = Field(default_factory=lambda: {"prompt_tokens": 0, "total_tokens": 0})


@app.post("/v1/embeddings", response_model=EmbeddingResponse)
async def embeddings(req: EmbeddingRequest) -> EmbeddingResponse:
    texts = [req.input] if isinstance(req.input, str) else req.input
    if not texts:
        raise HTTPException(400, "input 不能为空")

    model = get_embed_model()
    vecs = model.encode(texts, normalize_embeddings=True, show_progress_bar=False)
    vecs = np.asarray(vecs, dtype=np.float32)
    if vecs.shape[1] != EMBED_DIM:
        raise HTTPException(500, f"维度异常: {vecs.shape[1]} != {EMBED_DIM}")

    return EmbeddingResponse(data=[
        EmbeddingData(index=i, embedding=v.tolist()) for i, v in enumerate(vecs)
    ])


@app.get("/health")
async def health() -> dict:
    return {"status": "ok", "parser": PARSER_VERSION, "embed_model": EMBED_MODEL_NAME, "embed_dim": EMBED_DIM}
