"""Small, repeatable sidecar calibration probe for CIT-01.

Embedding similarity is deliberately reported separately from support labels:
contradictions are expected to be semantically close and must be sent to NLI.
"""
import json
import math
import os
import sys
import urllib.request


def cosine(left, right):
    dot = sum(a * b for a, b in zip(left, right))
    norm_left = math.sqrt(sum(a * a for a in left))
    norm_right = math.sqrt(sum(b * b for b in right))
    return dot / (norm_left * norm_right) if norm_left and norm_right else 0.0


def embed(url, texts):
    body = json.dumps({"model": "BAAI/bge-m3", "input": texts}).encode()
    request = urllib.request.Request(url, body, {"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=30) as response:
        payload = json.load(response)
    return [item["embedding"] for item in payload["data"]]


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    with open(os.path.join(root, "evaluation", "citation_cross_language_cases.json"), encoding="utf-8") as stream:
        cases = json.load(stream)
    url = os.environ.get("RP_SIDECAR_URL", "http://127.0.0.1:8100/v1/embeddings")
    vectors = embed(url, [text for case in cases for text in (case["claim"], case["excerpt"])])
    scores = []
    for index, case in enumerate(cases):
        score = cosine(vectors[index * 2], vectors[index * 2 + 1])
        scores.append(score)
        print(f"{case['id']}\t{case['label']}\t{score:.4f}")
    threshold = 0.55
    close = sum(score >= threshold for score in scores)
    positives = sum(case["label"] == "SUPPORTED" for case in cases)
    positive_close = sum(case["label"] == "SUPPORTED" and score >= threshold for case, score in zip(cases, scores))
    print(f"threshold={threshold:.2f} close={close}/{len(cases)} supported_recall={positive_close}/{positives}")
    print("interpretation=embedding is a candidate filter; contradictions remain close and require NLI")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"calibration unavailable: {error}", file=sys.stderr)
        sys.exit(2)
