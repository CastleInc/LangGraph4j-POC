# 🧩 AITs Matching Criteria: [(${criteriaDescription})]

Below are the AITs that match the given tech stack criteria:

---

<th:block th:each="ait : ${details}">

## 🎯 AIT: [(${ait.ait})]

<th:block th:if="${ait.languagesFrameworks != null}">

<th:block th:if="${ait.languagesFrameworks.languages != null}">
### 📝 Languages
<th:block th:each="lang : ${ait.languagesFrameworks.languages}">
- **[(${lang.name})]** <th:block th:if="${lang.version != null}">(Version: [(${lang.version})])</th:block>
</th:block>

</th:block>

<th:block th:if="${ait.languagesFrameworks.frameworks != null}">
### 🔧 Frameworks
<th:block th:each="fw : ${ait.languagesFrameworks.frameworks}">
- **[(${fw.name})]** <th:block th:if="${fw.version != null}">(Version: [(${fw.version})])</th:block>
</th:block>

</th:block>
</th:block>

<th:block th:if="${ait.infrastructure != null}">

<th:block th:if="${ait.infrastructure.databases != null}">
### 💾 Databases
<th:block th:each="db : ${ait.infrastructure.databases}">
- **[(${db.name})]** <th:block th:if="${db.version != null}">(Version: [(${db.version})])</th:block> <th:block th:if="${db.environment != null}">- Environment: **[(${db.environment})]**</th:block>
</th:block>

</th:block>

<th:block th:if="${ait.infrastructure.middlewares != null}">
### ⚙️ Middlewares
<th:block th:each="mw : ${ait.infrastructure.middlewares}">
- **[(${mw.type})]** <th:block th:if="${mw.version != null}">(Version: [(${mw.version})])</th:block> <th:block th:if="${mw.environment != null}">- Environment: **[(${mw.environment})]**</th:block>
</th:block>

</th:block>

<th:block th:if="${ait.infrastructure.operatingSystems != null}">
### 🖥️ Operating Systems
<th:block th:each="os : ${ait.infrastructure.operatingSystems}">
- **[(${os.name})]** <th:block th:if="${os.version != null}">(Version: [(${os.version})])</th:block> <th:block th:if="${os.environment != null}">- Environment: **[(${os.environment})]**</th:block>
</th:block>

</th:block>
</th:block>

<th:block th:if="${ait.libraries != null}">
### 📚 Libraries
<th:block th:each="lib : ${ait.libraries}">
- **[(${lib.name})]** <th:block th:if="${lib.version != null}">(Version: [(${lib.version})])</th:block>
</th:block>

</th:block>

---

</th:block>

**Total AITs Found:** [(${#lists.size(details)})]
