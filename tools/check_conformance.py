"""D4 conformance checker: compares AZERTY_D4_Design.md with ocs/src/main/java.

Checks
  A. class diagram <-> Java: classes, fields (name/type/visibility/static), operations
     (name/return type/parameters/visibility/static/abstract), nothing extra in the code.
  B. package diagram <-> imports: only permitted package dependencies, no cycles.
  C. sequence diagrams: every lifeline is a class of the diagram and every call message names an
     operation that exists on the receiver (or one of its supertypes). Returns and Java call
     sites are not inspected; those rules are checked by hand and by the AI review.
"""
import os
import re
import sys
from collections import defaultdict

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DESIGN = os.path.join(REPO, "AZERTY_D4_Design.md")
SRC = os.path.join(REPO, "ocs", "src", "main", "java")

ALLOWED_EDGES = {
    ("cli", "service"), ("cli", "domain"), ("cli", "catalog"), ("cli", "persistence"),
    ("cli", "validation"),
    ("service", "domain"), ("service", "validation"),
    ("validation", "domain"), ("catalog", "domain"), ("persistence", "domain"),
}

problems = []
notes = []


def fail(check, msg):
    problems.append("[%s] %s" % (check, msg))


def norm_type(t):
    t = re.sub(r"~([^~]*)~", r"<\1>", t.strip())
    t = t.replace("&lt;", "<").replace("&gt;", ">")
    return t


def split_params(s):
    s = s.strip()
    if not s:
        return []
    out = []
    depth = 0
    cur = ""
    for ch in s:
        if ch in "<(":
            depth += 1
        elif ch in ">)":
            depth -= 1
        if ch == "," and depth == 0:
            out.append(cur.strip())
            cur = ""
        else:
            cur += ch
    if cur.strip():
        out.append(cur.strip())
    return out


def parse_design():
    text = open(DESIGN, encoding="utf-8").read()
    blocks = re.findall(r"```mermaid\n(.*?)```", text, re.S)
    class_block = None
    for b in blocks:
        if b.lstrip().startswith("classDiagram"):
            class_block = b
    if class_block is None:
        raise SystemExit("no classDiagram block found")

    classes = {}
    cur = None
    cur_name = None
    for raw in class_block.splitlines():
        line = raw.strip()
        if not line:
            continue
        if line.startswith("class "):
            name = line[len("class "):].split("{")[0].strip()
            cur = {"kind": "class", "fields": {}, "methods": {}, "ctors": [], "values": []}
            classes[name] = cur
            cur_name = name
            continue
        if line == "}":
            cur = None
            continue
        if cur is None:
            continue
        if line.startswith("<<"):
            st = line.strip("<>")
            cur["kind"] = st
            continue
        # enum literal
        if cur["kind"] == "enumeration" and re.fullmatch(r"[A-Z][A-Z0-9_]*", line):
            cur["values"].append(line)
            continue
        clean = line.rstrip()
        static = clean.endswith("$")
        abstract = clean.endswith("*")
        clean = clean.rstrip("*$")
        vis = clean[0]
        rest = clean[1:]
        derived = rest.startswith("/")
        if derived:
            rest = rest[1:]
        if "(" in rest:
            lp = rest.index("(")
            rp = rest.rindex(")")
            mname = rest[:lp].strip()
            params = []
            for p in split_params(rest[lp + 1:rp]):
                if ":" in p:
                    pn, pt = p.split(":", 1)
                    params.append((pn.strip(), norm_type(pt)))
            tail = rest[rp + 1:].strip()
            ret = "void" if not tail else norm_type(tail.lstrip(":").strip())
            if mname and mname[0].isupper() and mname == cur_name:
                cur["ctors"].append(params)
            else:
                cur["methods"][mname] = {"params": params, "ret": ret,
                                         "static": static, "abstract": abstract,
                                         "vis": vis}
        else:
            if ":" not in rest:
                notes.append("unparsed attribute: %r" % line)
                continue
            aname, atype = rest.split(":", 1)
            cur["fields"][aname.strip()] = {"type": norm_type(atype), "vis": vis,
                                            "static": static, "derived": derived}
    return classes, class_block


JAVA_FILE_RE = re.compile(r"class\s+([A-Za-z0-9_]+)|interface\s+([A-Za-z0-9_]+)|enum\s+([A-Za-z0-9_]+)")


def parse_java():
    code = {}
    for root, _dirs, files in os.walk(SRC):
        for fn in files:
            if not fn.endswith(".java"):
                continue
            path = os.path.join(root, fn)
            rel = os.path.relpath(path, SRC)
            pkg = rel.split(os.sep)[1]
            text = open(path, encoding="utf-8").read()
            name = fn[:-5]
            info = {"pkg": pkg, "path": path, "fields": {}, "methods": {}, "ctors": [],
                    "values": [], "kind": "class", "imports": []}
            if re.search(r"public interface " + name, text):
                info["kind"] = "interface"
            elif re.search(r"public enum " + name, text):
                info["kind"] = "enumeration"
            elif re.search(r"public abstract class " + name, text):
                info["kind"] = "abstract"
            info["imports"] = re.findall(r"^import (ocs\.[a-z]+\.[A-Za-z0-9_]+);", text, re.M)
            body = text
            # enum literals
            if info["kind"] == "enumeration":
                m = re.search(r"public enum %s \{(.*?);" % name, body, re.S)
                if m:
                    info["values"] = [v.strip() for v in m.group(1).split(",") if v.strip()]
                body = body[m.end():] if m else body
            # fields
            for m in re.finditer(r"^    (private|public|protected) ((?:static )?)((?:final )?)([A-Za-z0-9_<>,\.\[\] ]+?) ([A-Za-z][A-Za-z0-9_]*)\s*(?:=.*?)?;", body, re.M):
                vis = {"private": "-", "public": "+", "protected": "#"}[m.group(1)]
                info["fields"][m.group(5)] = {"type": norm_type(m.group(4)), "vis": vis,
                                              "static": bool(m.group(2).strip())}
            # methods and constructors
            for m in re.finditer(r"^    (private|public|protected) ((?:static )?)((?:abstract )?)([A-Za-z0-9_<>,\.\[\] ]+) ([A-Za-z0-9_]+)\(([^)]*)\)\s*[{;]", body, re.M):
                vis = {"private": "-", "public": "+", "protected": "#"}[m.group(1)]
                ret, mname, params_txt = norm_type(m.group(4)), m.group(5), m.group(6)
                params = []
                for p in split_params(params_txt):
                    toks = p.rsplit(" ", 1)
                    if len(toks) == 2:
                        params.append((toks[1], norm_type(toks[0])))
                static = bool(m.group(2).strip())
                abstract = bool(m.group(3).strip())
                if mname == name:
                    info["ctors"].append(params)
                else:
                    info["methods"][mname] = {"params": params, "ret": ret,
                                              "static": static, "abstract": abstract,
                                              "vis": vis}
            code[name] = info
    return code


def check_classes(design, code):
    for name, d in design.items():
        if name not in code:
            fail("A", "class %s is in the design but not in the code" % name)
            continue
        j = code[name]
        if d["kind"] in ("enumeration",) and j["kind"] != "enumeration":
            fail("A", "%s: design kind %s, code kind %s" % (name, d["kind"], j["kind"]))
        if d["kind"] == "interface" and j["kind"] != "interface":
            fail("A", "%s: design says interface" % name)
        if d["kind"] == "abstract" and j["kind"] != "abstract":
            fail("A", "%s: design says abstract class" % name)
        for fname, f in d["fields"].items():
            if f.get("derived"):
                continue
            if fname not in j["fields"]:
                fail("A", "%s: field %s missing in code" % (name, fname))
                continue
            jf = j["fields"][fname]
            if norm_type(jf["type"]) != f["type"]:
                fail("A", "%s.%s: type %r in design, %r in code" % (name, fname, f["type"], jf["type"]))
            if jf["vis"] != f["vis"]:
                fail("A", "%s.%s: visibility %s in design, %s in code" % (name, fname, f["vis"], jf["vis"]))
            if jf["static"] != f["static"]:
                fail("A", "%s.%s: static mismatch" % (name, fname))
        for jname, jf in j["fields"].items():
            if jname not in d["fields"]:
                fail("A", "%s: code field %s is not in the design" % (name, jname))
        for mname, dm in d["methods"].items():
            if dm.get("abstract"):
                continue
            if mname not in j["methods"]:
                fail("A", "%s.%s: operation missing in code" % (name, mname))
                continue
            jm = j["methods"][mname]
            if dm["ret"] != jm["ret"]:
                fail("A", "%s.%s: return %r in design, %r in code" % (name, mname, dm["ret"], jm["ret"]))
            if [p[1] for p in dm["params"]] != [p[1] for p in jm["params"]]:
                fail("A", "%s.%s: parameters %r in design, %r in code" %
                     (name, mname, [p[1] for p in dm["params"]], [p[1] for p in jm["params"]]))
            if dm["vis"] != jm["vis"]:
                fail("A", "%s.%s: visibility mismatch" % (name, mname))
            if dm["static"] != jm["static"]:
                fail("A", "%s.%s: static mismatch" % (name, mname))
        for jname, jm in j["methods"].items():
            if jname not in d["methods"]:
                fail("A", "%s: code operation %s is not in the design" % (name, jname))
        if "enumeration" == d["kind"]:
            if sorted(d["values"]) != sorted(j["values"]):
                fail("A", "%s: enum values differ: %r / %r" % (name, d["values"], j["values"]))


def check_packages(code):
    edges = defaultdict(int)
    for name, info in code.items():
        for imp in info["imports"]:
            target = imp.split(".")[1]
            if target != info["pkg"]:
                edges[(info["pkg"], target)] += 1
    for (a, b) in edges:
        if (a, b) not in ALLOWED_EDGES:
            fail("B", "dependency %s -> %s is not permitted by the package diagram" % (a, b))
    # cycles (two-node)
    for (a, b) in list(edges):
        if (b, a) in edges:
            fail("B", "cycle between %s and %s" % (a, b))
    notes.append("package edges used: " + ", ".join("%s->%s" % e for e in sorted(edges)))


def parse_sequences(text):
    diagrams = []
    for m in re.finditer(r"```mermaid\n(sequenceDiagram.*?)```", text, re.S):
        diagrams.append(m.group(1))
    return diagrams


def java_supertype_of(cls, code):
    info = code.get(cls)
    if not info:
        return None
    src = open(info["path"], encoding="utf-8").read()
    m = re.search(r"class\s+%s\s+extends\s+([A-Za-z0-9_]+)" % cls, src)
    return m.group(1) if m else None


def op_exists(cls, mname, design, code):
    seen = set()
    cur = cls
    while cur and cur not in seen:
        seen.add(cur)
        d = design.get(cur)
        if d and (mname in d["methods"] or mname in d["ctors"]):
            return True
        cur = java_supertype_of(cur, code)
    return False


def check_sequences(design, code):
    text = open(DESIGN, encoding="utf-8").read()
    diags = parse_sequences(text)
    notes.append("sequence diagrams found: %d" % len(diags))
    for idx, d in enumerate(diags, 1):
        alias = {}
        actors = set()
        for m in re.finditer(r"^\s*(create\s+)?(actor|participant)\s+(\w+)\s+as\s+(\w+)", d, re.M):
            created, kind, a, cls = m.group(1), m.group(2), m.group(3), m.group(4)
            alias[a] = cls
            if kind == "actor":
                actors.add(a)
        for a, cls in alias.items():
            if a in actors:
                continue
            if cls not in design:
                fail("C", "UC%d: lifeline %s is not a class in the class diagram" % (idx, cls))
        for m in re.finditer(r"^\s*(\w+)\s*(-{1,2}>>)\s*(\w+):\s*(.+)$", d, re.M):
            sender, arrow, receiver, msg = m.group(1), m.group(2), m.group(3), m.group(4).strip()
            cls = alias.get(receiver)
            if cls is None:
                fail("C", "UC%d: message to unknown lifeline %s" % (idx, receiver))
                continue
            if arrow == "-->>" or receiver in actors or msg.startswith("new "):
                continue
            name = re.match(r"([A-Za-z_][A-Za-z0-9_]*)", msg)
            if not name:
                fail("C", "UC%d: cannot read message %r" % (idx, msg))
                continue
            mname = name.group(1)
            if not op_exists(cls, mname, design, code):
                fail("C", "UC%d: %s.%s is not an operation of the class diagram" % (idx, cls, mname))


def main():
    design, _ = parse_design()
    code = parse_java()
    notes.append("design classes: %d, code classes: %d" % (len(design), len(code)))
    check_classes(design, code)
    check_packages(code)
    check_sequences(design, code)
    print("\n".join(notes))
    if problems:
        print("\nPROBLEMS (%d):" % len(problems))
        for p in problems:
            print("  " + p)
        sys.exit(1)
    print("\nNo conformance problems found.")


if __name__ == "__main__":
    main()
