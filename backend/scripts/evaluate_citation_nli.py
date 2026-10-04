"""Run the small cross-language claim-support NLI calibration set through DeepSeek."""
import json
import os
import re
import sys
import urllib.request


def call_deepseek(cases):
    prompt = """You are a strict scientific claim-support verifier. The claim may be Chinese and the excerpt English.
For every item return exactly one label: SUPPORTED, CONTRADICTED, or INSUFFICIENT.
SUPPORTED means the excerpt entails the claim; CONTRADICTED means it entails the opposite; INSUFFICIENT means unrelated or not enough.
Return only a JSON array like [{\"id\":\"positive-1\",\"label\":\"SUPPORTED\"}].

Items:
"""
    for case in cases:
        prompt += f"\n{case['id']}\nClaim: {case['claim']}\nExcerpt: {case['excerpt']}\n"
    payload = json.dumps({"model": os.environ.get("DEEPSEEK_MODEL", "deepseek-chat"), "temperature": 0,
                          "messages": [{"role": "user", "content": prompt}]}).encode()
    request = urllib.request.Request(
        os.environ.get("DEEPSEEK_BASE_URL", "https://api.deepseek.com") + "/chat/completions", payload,
        {"Content-Type": "application/json", "Authorization": "Bearer " + os.environ["DEEPSEEK_API_KEY"]})
    with urllib.request.urlopen(request, timeout=90) as response:
        body = json.load(response)
    content = body["choices"][0]["message"]["content"].strip()
    content = re.sub(r"^```(?:json)?\s*|\s*```$", "", content).strip()
    return {item["id"]: item["label"].upper() for item in json.loads(content)}


def main():
    if not os.environ.get("DEEPSEEK_API_KEY"):
        print("DEEPSEEK_API_KEY is not set", file=sys.stderr)
        return 2
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    with open(os.path.join(root, "evaluation", "citation_cross_language_cases.json"), encoding="utf-8") as stream:
        cases = json.load(stream)
    predicted = call_deepseek(cases)
    labels = ["SUPPORTED", "CONTRADICTED", "INSUFFICIENT"]
    matrix = {actual: {label: 0 for label in labels} for actual in labels}
    for case in cases:
        matrix[case["label"]][predicted.get(case["id"], "INSUFFICIENT")] += 1
    correct = sum(matrix[label][label] for label in labels)
    print("id\texpected\tpredicted")
    for case in cases:
        print(f"{case['id']}\t{case['label']}\t{predicted.get(case['id'], 'INSUFFICIENT')}")
    print(f"accuracy={correct}/{len(cases)}")
    print("confusion_matrix=" + json.dumps(matrix, ensure_ascii=False, sort_keys=True))
    return 0 if correct == len(cases) else 1


if __name__ == "__main__":
    raise SystemExit(main())
