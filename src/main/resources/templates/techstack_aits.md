# 🧩 AITs Using [(${component})]

Below are the AITs that have **[(${component})]** in their tech stack:

---

[# th:each="ait : ${list}"]
[# th:if="${ait != null and ait.ait != null}"]

## AIT: [(${ait.ait})]

[# th:if="${ait.languagesFrameworks != null}"]
[# th:if="${ait.languagesFrameworks.languages != null and not #lists.isEmpty(ait.languagesFrameworks.languages)}"]

### 📝 Languages
[# th:each="lang : ${ait.languagesFrameworks.languages}"][# th:if="${lang != null and lang.name != null}"]
- **[(${lang.name})]**[# th:if="${lang.version != null and not #strings.isEmpty(lang.version)}"] ([(${lang.version})])[/]
[/][/]

[/]

[# th:if="${ait.languagesFrameworks.frameworks != null and not #lists.isEmpty(ait.languagesFrameworks.frameworks)}"]

### 🔧 Frameworks
[# th:each="fw : ${ait.languagesFrameworks.frameworks}"][# th:if="${fw != null and fw.name != null}"]
- **[(${fw.name})]**[# th:if="${fw.version != null and not #strings.isEmpty(fw.version)}"] ([(${fw.version})])[/]
[/][/]

[/]
[/]

[# th:if="${ait.infrastructure != null}"]
[# th:if="${ait.infrastructure.databases != null and not #lists.isEmpty(ait.infrastructure.databases)}"]

### 💾 Databases
[# th:each="db : ${ait.infrastructure.databases}"][# th:if="${db != null and db.name != null}"]
- **[(${db.name})]**[# th:if="${db.version != null and not #strings.isEmpty(db.version)}"] ([(${db.version})])[/][# th:if="${db.environment != null and not #strings.isEmpty(db.environment)}"] [[(${db.environment})]][/]
[/][/]

[/]

[# th:if="${ait.infrastructure.middlewares != null and not #lists.isEmpty(ait.infrastructure.middlewares)}"]

### ⚙️ Middlewares
[# th:each="mw : ${ait.infrastructure.middlewares}"][# th:if="${mw != null and mw.type != null}"]
- **[(${mw.type})]**[# th:if="${mw.version != null and not #strings.isEmpty(mw.version)}"] ([(${mw.version})])[/][# th:if="${mw.environment != null and not #strings.isEmpty(mw.environment)}"] [[(${mw.environment})]][/]
[/][/]

[/]

[# th:if="${ait.infrastructure.operatingSystems != null and not #lists.isEmpty(ait.infrastructure.operatingSystems)}"]

### 🖥️ Operating Systems
[# th:each="os : ${ait.infrastructure.operatingSystems}"][# th:if="${os != null and os.name != null}"]
- **[(${os.name})]**[# th:if="${os.version != null and not #strings.isEmpty(os.version)}"] ([(${os.version})])[/][# th:if="${os.environment != null and not #strings.isEmpty(os.environment)}"] [[(${os.environment})]][/]
[/][/]

[/]
[/]

[# th:if="${ait.libraries != null and not #lists.isEmpty(ait.libraries)}"]

### 📚 Libraries
[# th:each="lib : ${ait.libraries}"][# th:if="${lib != null and lib.name != null}"]
- [(${lib.name})][# th:if="${lib.version != null and not #strings.isEmpty(lib.version)}"] ([(${lib.version})])[/]
[/][/]

[/]

---

[/]
[/]

**Total AITs Found:** [(${#lists.size(list)})]

