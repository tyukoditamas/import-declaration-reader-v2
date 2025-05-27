#!/usr/bin/env python3
import logging

logging.getLogger("pdfminer").setLevel(logging.ERROR)
logging.getLogger("pdfminer.pdfpage").setLevel(logging.ERROR)

import sys, json
from pathlib import Path
import pdfplumber
import re


def extract_fields(text):
    lines = [l.strip() for l in text.splitlines() if l.strip()]
    data = {}

    # 1. nrDestinatar
    for i,l in enumerate(lines):
        if "Destinatar" in l:
            m = re.search(r"Nr\W*([A-Z0-9]+)", l)
            if m:
                data["nrDestinatar"] = m.group(1)
            break

    # 2. mrn
    for l in lines:
        if l.upper().startswith("MRN"):
            parts = re.split(r"[:\s-]+", l, maxsplit=1)
            if len(parts)==2:
                data["mrn"] = parts[1].strip()
            break

    # 3. nrDeReferinta
    m = re.search(r'LRN\s*[:\s]*([A-Za-z0-9-]+)', text, re.IGNORECASE)
    if m:
        data["nrDeReferinta"] = m.group(1)

    # 4. valoareStatistica
    for l in lines:
        if "valoarea statistica" in l.lower():
            m = re.search(r"([\d\.,]+)", l)
            if m:
                data["valoareStatistica"] = m.group(1)
            break

    # 5. depozitPlataAnticipata
    for l in lines:
        if "depozit plata anticipa" in l.lower():
            m = re.search(r"([\d\.,]+)", l)
            if m:
                data["depozitPlataAnticipata"] = m.group(1)
            break

    # 6. referintaDocument
    for l in lines:
        if l.startswith("Z822"):
            m = re.search(r'Z822\s+(.+?\s*/\s*\d{2}\.\d{2}\.\d{4})', l)
            if m:
                data["referintaDocument"] = m.group(1).strip()
            break

    # 7. totalPlataA00
    total_idx = None
    for idx, l in enumerate(lines):
        if l.lower().startswith("total plata"):
            total_idx = idx
            break
    if total_idx is not None:
        for l in lines[total_idx + 1:]:
            if l.startswith("A00"):
                m = re.search(r"A00\W*([\d\.,]+)", l)
                if m:
                    data["totalPlataA00"] = m.group(1)
                break

    return data

def main(folder_path):
    results = []
    for pdf_path in Path(folder_path).glob("*.pdf"):
        try:
            with pdfplumber.open(pdf_path) as pdf:
                text = "\n".join(page.extract_text() or "" for page in pdf.pages)
            rec = extract_fields(text)
            rec["file"] = pdf_path.name
        except Exception as e:
            rec = {"file": pdf_path.name, "error": str(e)}
        results.append(rec)

    # emit JSON array to stdout
    print(json.dumps(results, ensure_ascii=False))

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: extract.py <folder>", file=sys.stderr)
        sys.exit(1)
    main(sys.argv[1])

