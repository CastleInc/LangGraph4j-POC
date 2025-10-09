# 🧩 AITs Matching: [(${criteriaDescription})]

Below are the AITs matching your search criteria:

---

<th:block th:each="ait : ${details}">

## 🎯 AIT: [(${ait.ait})]

<th:block th:if="${ait.languagesFrameworks != null}">

<th:block th:if="${showLanguages and ait.languagesFrameworks.languages != null and !ait.languagesFrameworks.languages.isEmpty()}">
### 📝 Languages
<th:block th:each="lang : ${ait.languagesFrameworks.languages}">
- **[(${lang.name})]**<th:block th:if="${lang.version != null}"> (v[(${lang.version})])</th:block>
</th:block>

</th:block>

<th:block th:if="${showFrameworks and ait.languagesFrameworks.frameworks != null and !ait.languagesFrameworks.frameworks.isEmpty()}">
### 🔧 Frameworks
<th:block th:each="fw : ${ait.languagesFrameworks.frameworks}">
- **[(${fw.name})]**<th:block th:if="${fw.version != null}"> (v[(${fw.version})])</th:block>
</th:block>

</th:block>
</th:block>

<th:block th:if="${ait.infrastructure != null}">

<th:block th:if="${showDatabases and ait.infrastructure.databases != null and !ait.infrastructure.databases.isEmpty()}">
### 💾 Databases
<th:block th:each="db : ${ait.infrastructure.databases}">
- **[(${db.name})]**<th:block th:if="${db.version != null}"> (v[(${db.version})])</th:block><th:block th:if="${db.environment != null}"> - *[(${db.environment})]*</th:block>
</th:block>

</th:block>

<th:block th:if="${showMiddlewares and ait.infrastructure.middlewares != null and !ait.infrastructure.middlewares.isEmpty()}">
### ⚙️ Middlewares
<th:block th:each="mw : ${ait.infrastructure.middlewares}">
- **[(${mw.type})]**<th:block th:if="${mw.version != null}"> (v[(${mw.version})])</th:block><th:block th:if="${mw.environment != null}"> - *[(${mw.environment})]*</th:block>
</th:block>

</th:block>

<th:block th:if="${showOS and ait.infrastructure.operatingSystems != null and !ait.infrastructure.operatingSystems.isEmpty()}">
### 🖥️ Operating Systems
<th:block th:each="os : ${ait.infrastructure.operatingSystems}">
- **[(${os.name})]**<th:block th:if="${os.version != null}"> (v[(${os.version})])</th:block><th:block th:if="${os.environment != null}"> - *[(${os.environment})]*</th:block>
</th:block>

</th:block>
</th:block>

<th:block th:if="${showLibraries and ait.libraries != null and !ait.libraries.isEmpty()}">
### 📚 Libraries
<th:block th:each="lib : ${ait.libraries}">
- **[(${lib.name})]**<th:block th:if="${lib.version != null}"> (v[(${lib.version})])</th:block>
</th:block>

</th:block>

---

</th:block>

**Total AITs Found:** [(${#lists.size(details)})]
