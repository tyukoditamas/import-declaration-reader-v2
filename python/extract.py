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

    # 3. referintaDocument
    for l in lines:
        if l.startswith("Z821"):
            m = re.search(r'Z821\s+(.+?\s*/\s*\d{2}\.\d{2}\.\d{4})', l)
            if m:
                data["referintaDocument"] = m.group(1).strip()
            break

    # 4. nrArticole: section header “5 Articole” → next line only
        for i, l in enumerate(lines):
            if "5 Articole" in l:
                # look forward until we hit a line starting with digits
                for j in range(i+1, len(lines)):
                    m = re.match(r"^(\d+)\b", lines[j])
                    if m:
                        data["nrArticole"] = m.group(1)
                        break
                break


    # 5. nrContainer: first look for “Containere” → next line
    for i, l in enumerate(lines):
        if "Numar container" in l:
            if i + 1 < len(lines):
                nxt = lines[i + 1]
                m = re.match(r"([A-Z0-9\-]+)", nxt)
                if m:
                    data["nrContainer"] = m.group(1)
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

