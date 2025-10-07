# 🧩 AITs Matching Criteria: [(${criteriaDescription})]

Below are the AITs that match the given tech stack criteria:

---

[# th:each="ait : ${details}"]

## 🎯 AIT: [(${ait.ait})]

[# th:if="${ait.languagesFrameworks != null}"]

[# th:if="${ait.languagesFrameworks.languages != null}"]
### 📝 Languages
[# th:each="lang : ${ait.languagesFrameworks.languages}"]
- **[(${lang.name})]** [# th:if="${lang.version != null}"](Version: [(${lang.version})])[/]
[/]
[/]

[# th:if="${ait.languagesFrameworks.frameworks != null}"]
### 🔧 Frameworks
[# th:each="fw : ${ait.languagesFrameworks.frameworks}"]
- **[(${fw.name})]** [# th:if="${fw.version != null}"](Version: [(${fw.version})])[/]
[/]
[/]

[/]

[# th:if="${ait.infrastructure != null}"]

[# th:if="${ait.infrastructure.databases != null}"]
### 💾 Databases
[# th:each="db : ${ait.infrastructure.databases}"]
- **[(${db.name})]** [# th:if="${db.version != null}"](Version: [(${db.version})])[/] [# th:if="${db.environment != null}"]- Environment: **[(${db.environment})]**[/]
[/]
[/]

[# th:if="${ait.infrastructure.middlewares != null}"]
### ⚙️ Middlewares
[# th:each="mw : ${ait.infrastructure.middlewares}"]
- **[(${mw.type})]** [# th:if="${mw.version != null}"](Version: [(${mw.version})])[/] [# th:if="${mw.environment != null}"]- Environment: **[(${mw.environment})]**[/]
[/]
[/]

[# th:if="${ait.infrastructure.operatingSystems != null}"]
### 🖥️ Operating Systems
[# th:each="os : ${ait.infrastructure.operatingSystems}"]
- **[(${os.name})]** [# th:if="${os.version != null}"](Version: [(${os.version})])[/] [# th:if="${os.environment != null}"]- Environment: **[(${os.environment})]**[/]
[/]
[/]

[/]

[# th:if="${ait.libraries != null}"]
### 📚 Libraries
[# th:each="lib : ${ait.libraries}"]
- **[(${lib.name})]** [# th:if="${lib.version != null}"](Version: [(${lib.version})])[/]
[/]
[/]

---

[/]

**Total AITs Found:** [(${#lists.size(details)})]

