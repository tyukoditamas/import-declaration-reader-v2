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
        if "Importatorul - [13 04]" in l:
            m = re.search(r"Nr\W*([A-Z0-9]+)", l)
            if m:
                data["nrDestinatar"] = m.group(1)
            break

    # 2. mrn
    for l in lines:
        if "MRN" not in l.upper():
            continue
        m = re.search(r"\bMRN\W*([A-Z0-9]+)", l, re.IGNORECASE)
        if m:
            data["mrn"] = m.group(1)
        break

    # 3. referintaDocument
    for l in lines:
        # Match any line with N822 or N821
        if "N822" in l or "N821" in l:
            # Option A: simple split+strip
            parts = l.split("/", 1)
            if len(parts) > 1:
                data["referintaDocument"] = parts[1].strip().split()[0]
            else:
                # Fallback: regex for either N822 or N821
                m = re.search(r"N82[12]\s*/\s*(\S+)", l)
                if m:
                    data["referintaDocument"] = m.group(1)
            break  # stop after first match

    # 4. nrArticole: section header “5 Articole” → next line only
    for l in lines:
        if "total articole" in l.lower():
            # try to grab it from the same line first
            m = re.search(r"total articole[^\d]*(\d+)", l, re.IGNORECASE)
            if m:
                data["nrArticole"] = m.group(1)
            break


    # 5. nrContainer: first look for “Containere” → next line

    for l in lines:
        if "Numărul de identificare al containerului" in l:
            # DEBUG: see exactly what this line contains
            # print("Container-line:", repr(l))

            # Option A: split on the closing bracket
            parts = l.split("]", 1)
            if len(parts) > 1:
                # everything after the first ']'
                # then split on whitespace and take the first token
                data["nrContainer"] = parts[1].strip().split()[0]
            else:
                # Fallback: regex search anywhere on this line
                m = re.search(r"\[19 07\]\s*([A-Z0-9\-]+)", l)
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

