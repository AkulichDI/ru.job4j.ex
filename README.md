/* ═════════════════════════════════════════════════════════════════════════════════════

 * ═════════════════════════════════════════════════════════════════════════════════════ */

/* ═════ ВСТАВЬТЕ СВОЙ БОЛЬШОЙ СПИСОК/CSV С ФИО ═════ */
def CSV_TEXT = $/
Иванов Иван Иванович
Петров Пётр Петрович
/$

/* ═════ КОНФИГУРАЦИЯ ═════ */
def DRY_RUN = false                           // ⚠️ Для теста поставьте true
long MAX_PROCESSING_TIME_MS = 240000          // ⏱️ Ровно 4 минуты
int  MAX_EDITS_PER_EMPLOYEE = 1000
int  MAX_TOTAL_EDITS = 50000
char DELIM = ','
int  FIO_COL = 0

// Классы/атрибуты связей (как в v6.0)
List<String> CLASSES = ['serviceCall', 'task']
// Перечень ссылочных полей на сотрудника (system code из модели)
List<String> REL_ATTRS = [
  'responsibleEmployee','executor','assignee','author',
  'clientEmployee','initiator','manager','observer'
]
// Куда назначаем подразделение
List<String> OU_TARGET_FIELDS = ['responsibleOu','ou']

// Статусы (лексика как в v6.0)
Set<String> CLOSE_STATUS_CODES  = ['resolved','разрешен','разрешено','разрешён'] as Set
Set<String> CLOSED_STATUS_CODES = ['closed','закрыт','закрыто'] as Set
Set<String> SKIP_STATUS_CODES = (CLOSE_STATUS_CODES + CLOSED_STATUS_CODES + [
  'canceled','cancelled','done','completed','finished','archived'
]) as Set
Set<String> SKIP_STATUS_TITLES = [
  'разрешен','разрешено','разрешён','закрыт','закрыто',
  'отклонен','отклонено','отклонён','выполнен','выполнено',
  'решено','решён','завершен','завершено','завершён',
  'отменен','отменено','отменён','архив'
] as Set

/* ═════ Белые списки полей (исключаем 0=1 и лишнюю нагрузку) ═════ */
def ATTRS_BY_CLASS = [
  serviceCall: ([
    'responsibleEmployee','author','clientEmployee','initiator','manager','observer'
  ] as Set),
  task: ([
    'responsibleEmployee','executor','assignee','author','initiator','observer'
  ] as Set)
] as Map<String, Set<String>>

def CLOSE_ATTRS_BY_CLASS = [
  serviceCall: (['responsibleEmployee','initiator'] as Set),
  task:        (['responsibleEmployee']            as Set)
] as Map<String, Set<String>>

/* ═════ ВЕРИФИКАЦИЯ ИЗМЕНЕНИЙ ═════ */
boolean VERIFY_TASK_CHANGES = true
boolean VERIFY_EMP_CHANGES  = true

/* ═════ ЛОГ-СВОДКА через logger INFO (без спама) ═════ */
boolean LOG_SUMMARY_TO_LOGGER  = true
String  LOG_TITLE              = "Архивирование"   // обяз. title для кастомного лога
String  LOG_TAG                = "[архивирование]"
int     LOG_MAX_LINES          = 20                // общий бюджет строк
int     LOG_CHUNK_NAMES        = 25                // ФИО в одной строке
boolean LOG_SORT_NAMES         = true

/* ═════ КОМПАКТНЫЙ ОТЧЁТ (return ≤ 50k) ═════ */
int REPORT_SOFT_LIMIT     = 48000
int REPORT_LIST_MAX_ITEMS = 50
int REPORT_LIST_CHUNK     = 25

def report = new StringBuilder(4096)
def appendLine = { String s ->
  if (report.length() + s.length() + 1 <= REPORT_SOFT_LIMIT) {
    report.append(s).append('\n')
  } else if (!report.toString().endsWith("… [вывод обрезан]\n")) {
    report.append("… [вывод обрезан]\n")
  }
}
def emitList = { String label, List<String> items ->
  int count = items?.size() ?: 0
  appendLine("${label}: ${count}")
  if (!items || count == 0) return
  def arr = LOG_SORT_NAMES ? new ArrayList(items) : new ArrayList(items)
  if (LOG_SORT_NAMES) java.util.Collections.sort(arr)
  int n = Math.min(count, REPORT_LIST_MAX_ITEMS)
  for (int i = 0; i < n; i += REPORT_LIST_CHUNK) {
    int j = Math.min(n, i + REPORT_LIST_CHUNK)
    appendLine("  " + arr.subList(i, j).join(', '))
  }
  if (count > n) appendLine("  ... и ещё ${count - n}")
}
def emitKV = { String key, Object val ->
  appendLine(String.format("%-36s %s", key + ":", String.valueOf(val)))
}

/* ═════ ВРЕМЯ/ТРАНЗАКЦИИ ═════ */
def startTime = System.currentTimeMillis()
def checkTimeout = { -> (System.currentTimeMillis() - startTime) >= MAX_PROCESSING_TIME_MS }
def inTx = { Closure c ->
  try {
    if (this.metaClass.hasProperty(this,'api') && api?.tx) return api.tx.call { c.call() }
    return c.call()
  } catch (ignored) { return null }
}

/* ═════ ХЕЛПЕРЫ ═════ */
def splitCsv = { String line ->
  def res = []; def cur = new StringBuilder(); boolean inQuotes = false
  for (int i=0;i<line.length();i++){
    char ch=line.charAt(i)
    if (ch=='"'){
      if (inQuotes && i+1<line.length() && line.charAt(i+1)=='"'){ cur.append('"'); i++ }
      else { inQuotes = !inQuotes }
    } else if (ch==DELIM && !inQuotes){
      res.add(cur.toString().trim()); cur.setLength(0)
    } else { cur.append(ch) }
  }
  res.add(cur.toString().trim()); return res
}
def buildFioList = { String csvText ->
  def fioList = [] as List<String>
  csvText.readLines().each { line ->
    def trimmed = line?.trim()
    if (!trimmed || trimmed.startsWith('#') || trimmed.startsWith('//')) return
    try {
      def cols = splitCsv(line)
      def fioCell = cols.size()>FIO_COL ? cols[FIO_COL] : ''
      def normalized = fioCell?.replace('\u00A0',' ')?.replaceAll(/\s+/, ' ')?.trim()
      if (!normalized) return
      def words = normalized.tokenize(' ')
      if (words.size()<2) return
      def fio = words.take(3).join(' ')
      if (!fioList.contains(fio)) fioList.add(fio)
    } catch (ignored){}
  }
  return fioList
}
def normalizeFio = { String s ->
  (s ?: '').replace('\u00A0',' ').replaceAll(/\s+/, ' ')
           .replace('ё','е').replace('Ё','Е').trim()
}
def toObj = { any -> try { (any instanceof String) ? utils.get(any) : any } catch (ignored){ any } }
def refetch = { obj -> try { obj?.UUID ? utils.get(obj.UUID) : obj } catch (ignored){ obj } }

/* Поиск сотрудника — как в v6.0 */
def findEmployeeByFio = { String fioInput ->
  try {
    def fio = normalizeFio(fioInput); if (!fio) return null
    try { def f=utils.find('employee',[title:fio], sp.ignoreCase()); if (f?.size()==1) return toObj(f[0]) } catch(ignored){}
    try { def f=utils.find('employee',[title:op.like("%${fio}%")], sp.ignoreCase()); if (f?.size()==1) return toObj(f[0]) } catch(ignored){}
    def parts = fio.tokenize(' ')
    if (parts.size()>=2){
      try { def f=utils.find('employee',[lastName:parts[0], firstName:parts[1]], sp.ignoreCase()); if (f?.size()==1) return toObj(f[0]) } catch(ignored){}
    }
    return null
  } catch (e){ return null }
}
def getStatusInfo = { obj ->
  try {
    if (!obj) return ['', '']
    def code=''; def title=''
    ['status','state','stage'].each { field ->
      try {
        def st = obj."${field}"
        if (st){
          if (!code)  code  = st.code ?.toString()?.toLowerCase() ?: ''
          if (!title) title = st.title?.toString()?.toLowerCase() ?: ''
        }
      } catch (ignored){}
    }
    return [code,title]
  } catch (e){ return ['', ''] }
}
def getEmployeeDepartment = { emp ->
  try {
    if (!emp) return null
    def parent = emp.parent; if (!parent) return null
    def uuid = parent?.UUID; if (!uuid) return null
    def normalizedUuid = uuid.toString()
    if (!normalizedUuid.contains('$')) normalizedUuid = "ou\$${uuid}"
    return normalizedUuid
  } catch (e){ return null }
}
def alreadyAssignedTo = { obj, String field, String targetUuid ->
  try {
    def value = obj."${field}"; if (!value) return false
    def currentUuid = value?.UUID?.toString() ?: (value instanceof String ? value : null)
    if (!currentUuid) return false
    if (currentUuid == targetUuid) return true
    def extractId = { u -> u.contains('$') ? u.split('\\$',2)[1] : u }
    return extractId(currentUuid) == extractId(targetUuid)
  } catch (e){ return false }
}

/* Безопасный utils.find: пропускаем запросы по несуществующим полям (нет 0=1) */
def safeFind = { String cls, String attr, Object value ->
  if (!value) return Collections.emptyList()
  def allowed = ATTRS_BY_CLASS[cls]
  if (allowed == null || !allowed.contains(attr)) return Collections.emptyList()
  try { return utils.find(cls, [(attr): value]) } catch (ignored) { return Collections.emptyList() }
}

/* Сбор "разрешённых" для закрытия (узкий поиск) */
def findTasksToClose = { emp ->
  def tasks = []; def seen = new HashSet()
  try {
    CLASSES.each { cls ->
      def attrs = CLOSE_ATTRS_BY_CLASS[cls] ?: (['responsibleEmployee'] as Set)
      attrs.each { attr ->
        if (checkTimeout()) return tasks
        def list = safeFind(cls, attr, emp)
        list?.each { o -> if (o?.UUID && seen.add(o.UUID)) tasks.add(o) }
      }
    }
  } catch (ignored){}
  return tasks
}

/* Сбор всех связанных (для переназначения) */
def findAllRelatedObjects = { emp ->
  def related = []; def seen = new HashSet()
  CLASSES.each { cls ->
    def attrs = ATTRS_BY_CLASS[cls] ?: Collections.<String>emptySet()
    attrs.each { attr ->
      if (checkTimeout()) return related
      def list = safeFind(cls, attr, emp)
      list?.each { o -> if (o?.UUID && seen.add(o.UUID)) related.add(o) }
    }
  }
  return related
}

/* Применение изменений + верификация */
def tryAssign = { obj, List fields, String targetUuid ->
  for (String field : fields) {
    try {
      if (alreadyAssignedTo(obj, field, targetUuid)) return 'already'
      if (DRY_RUN) return 'assigned' // в тесте не считаем как «реально изменено»
      inTx { utils.edit(obj, [(field): targetUuid]) }
      if (VERIFY_TASK_CHANGES) {
        def fresh = refetch(obj)
        if (alreadyAssignedTo(fresh, field, targetUuid)) return 'assigned'
        else continue
      } else return 'assigned'
    } catch (ignored){}
  }
  return 'failed'
}
def tryCloseResolvedTask = { obj ->
  try {
    def (code,title) = getStatusInfo(obj)
    boolean isResolved = CLOSE_STATUS_CODES.contains(code) || title.contains('разрешен') || title.contains('разрешё')
    if (!isResolved) return false
    if (DRY_RUN) return false // в тесте не считаем «реально закрыто»
    try { inTx { utils.edit(obj, [status:[code:'closed']]) } } catch (ignored){}
    try { inTx { utils.edit(obj, [state : 'closed']) } } catch (ignored){}
    if (VERIFY_TASK_CHANGES) {
      def fresh = refetch(obj)
      def (c2,t2) = getStatusInfo(fresh)
      return (c2 == 'closed' || t2.contains('закрыт'))
    } else return true
  } catch (e){ return false }
}

/* Лицензия → notLicensed (с верификацией) */
def updateLicense = { emp ->
  try {
    def cur = emp?.license
    boolean alreadyNot = false
    if (cur instanceof String) {
      def s = cur.toLowerCase(); alreadyNot = s.contains('notlicensed') || s.contains('нелиценз')
    } else if (cur?.code) {
      alreadyNot = cur.code.toString().toLowerCase().contains('notlicensed')
    } else if (cur?.title) {
      def t = cur.title.toString().toLowerCase(); alreadyNot = t.contains('notlicensed') || t.contains('нелиценз')
    }
    if (alreadyNot) return [false, true] // уже было

    if (DRY_RUN) return [false, false]
    inTx { utils.edit(emp, [license: 'notLicensed']) }
    if (VERIFY_EMP_CHANGES) {
      def fresh = refetch(emp)
      def lc = fresh?.license
      boolean ok = false
      if (lc instanceof String) {
        def s = lc.toLowerCase(); ok = s.contains('notlicensed') || s.contains('нелиценз')
      } else if (lc?.code) {
        ok = lc.code.toString().toLowerCase().contains('notlicensed')
      } else if (lc?.title) {
        def t = lc.title.toString().toLowerCase(); ok = t.contains('notlicensed') || t.contains('нелиценз')
      }
      return [ok, false]
    } else return [true, false]
  } catch (e){ return [false, false] }
}

/* Архивирование → removed=true (с верификацией) */
def archiveEmployee = { emp ->
  try {
    if (emp?.removed == true) return [false, true]   // уже был в архиве
    if (DRY_RUN) return [false, false]
    inTx { utils.edit(emp, [removed: true]) }
    if (VERIFY_EMP_CHANGES) {
      def fresh = refetch(emp)
      return [fresh?.removed == true, false]
    } else return [true, false]
  } catch (e) { return [false, false] }
}

/* ═════ ОСНОВНОЙ ЦИКЛ ═════ */
def fioList = buildFioList(CSV_TEXT)
if (!fioList || fioList.isEmpty()) {
  appendLine("CSV/текст пуст — нет ФИО для обработки.")
  return report.toString()
}

// Счётчики реальных изменений
int processedEmployees = 0
int tasksClosedTotal = 0
int tasksReassignedTotal = 0
int licensesChanged = 0
int licensesAlreadyNot = 0
int archivedOk = 0
int totalEdits = 0

// Списки ФИО
def notFoundEmployees = [] as List<String>
def noDepartmentEmployees = [] as List<String>
def licenseFailed = [] as List<String>
def archiveFailed = [] as List<String>
def timedOutNotProcessed = [] as List<String>
def licenseChangedEmployees = [] as List<String>         // у кого ЛИЦЕНЗИЯ реально снята
def licenseAlreadyEmployees = [] as List<String>         // у кого уже была notLicensed
def archivedEmployees = [] as List<String>               // реально архивированы
def alreadyArchivedEmployees = [] as List<String>        // уже были archived

for (int i=0; i<fioList.size(); i++) {
  if (checkTimeout()) { timedOutNotProcessed.addAll(fioList.subList(i, fioList.size())); break }

  def fio = fioList[i]
  def emp = findEmployeeByFio(fio)
  if (!emp) { notFoundEmployees << fio; continue }

  def departmentUuid = getEmployeeDepartment(emp)
  if (!departmentUuid) { noDepartmentEmployees << fio; continue }

  // === 1) "разрешен" → "закрыт" ===
  def toClose = findTasksToClose(emp) ?: []
  int closedForEmp = 0
  for (obj in toClose) {
    if (checkTimeout()) { timedOutNotProcessed.addAll(fioList.subList(i+1, fioList.size())); break }
    if (totalEdits >= MAX_TOTAL_EDITS) break
    if (tryCloseResolvedTask(obj)) {
      closedForEmp++; tasksClosedTotal++; totalEdits++
      if (closedForEmp >= MAX_EDITS_PER_EMPLOYEE) break
    }
  }

  // === 2) Прочие открытые → переназначение на подразделение ===
  def related = findAllRelatedObjects(emp) ?: []
  int reassignedForEmp = 0
  for (obj in related) {
    if (checkTimeout()) { timedOutNotProcessed.addAll(fioList.subList(i+1, fioList.size())); break }
    if (totalEdits >= MAX_TOTAL_EDITS) break
    def (code,title) = getStatusInfo(obj)
    if (SKIP_STATUS_CODES.contains(code) || SKIP_STATUS_TITLES.any { title.contains(it) }) continue
    def res = tryAssign(obj, OU_TARGET_FIELDS, departmentUuid)
    if (res == 'assigned') {
      reassignedForEmp++; tasksReassignedTotal++; totalEdits++
      if (reassignedForEmp >= MAX_EDITS_PER_EMPLOYEE) break
    }
  }

  // === 3) Лицензия ===
  def (licChanged, licAlready) = updateLicense(emp)
  if (licChanged) { licensesChanged++; licenseChangedEmployees << fio }
  if (licAlready) { licensesAlreadyNot++; licenseAlreadyEmployees << fio }
  if (!licChanged && !licAlready && !DRY_RUN) licenseFailed << fio

  // === 4) Архивирование ===
  def (archChanged, archAlready) = archiveEmployee(emp)
  if (archChanged) { archivedOk++; archivedEmployees << fio }
  else if (archAlready) { alreadyArchivedEmployees << fio }
  else if (!DRY_RUN) { archiveFailed << fio }

  processedEmployees++

  // Очистка локальных ссылок
  toClose?.clear(); related?.clear()
  toClose = null; related = null; emp = null

  if (checkTimeout()) { timedOutNotProcessed.addAll(fioList.subList(i+1, fioList.size())); break }
}

/* ═════ ИТОГОВЫЙ ОТЧЁТ (return) ═════ */
appendLine("=== СВОДКА (DRY_RUN=${DRY_RUN ? 'ON' : 'OFF'}) ===")
emitKV("Обработано сотрудников", "${processedEmployees} из ${fioList.size()}")
emitKV("Закрыто задач (\"разрешен\" → \"закрыт\")", tasksClosedTotal)
emitKV("Переназначено открытых задач", tasksReassignedTotal)
emitKV("Лицензий снято (→ notLicensed)", "${licensesChanged} (уже были: ${licensesAlreadyNot})")
emitKV("Архивировано (removed=true)", archivedOk)

emitList("Лицензия снята у (ФИО)",                  licenseChangedEmployees)
emitList("Уже были notLicensed (ФИО)",              licenseAlreadyEmployees)
emitList("Архивированы (ФИО)",                      archivedEmployees)
emitList("Уже были в архиве (removed=true)",        alreadyArchivedEmployees)
emitList("Не удалось снять лицензию",               licenseFailed)
emitList("Не архивированы (removed=true не выставлен)", archiveFailed)
emitList("Не найдены по ФИО",                       notFoundEmployees)
emitList("Без подразделения (parent)",              noDepartmentEmployees)
emitList("Не обработаны из-за таймаута",            timedOutNotProcessed)

/* ═════ ЛОГ-СВОДКА через logger (INFO) ═════ */
if (LOG_SUMMARY_TO_LOGGER) {
  int _lines = 0
  def canLog = { -> _lines < LOG_MAX_LINES }
  def logInfo = { String msg ->
    if (!canLog()) return
    try { logger?.info([title: LOG_TITLE, message: "${LOG_TAG} ${msg}"]); _lines++; return } catch (ignored) {}
    try { logger?.info(LOG_TITLE, "${LOG_TAG} ${msg}"); _lines++; return } catch (ignored) {}
    try { logger?.info("${LOG_TAG} ${LOG_TITLE}: ${msg}"); _lines++; return } catch (ignored) {}
  }
  def chunked = { List<String> xs, int size ->
    def out = []; if (!xs) return out
    def s = LOG_SORT_NAMES ? new ArrayList(xs) : new ArrayList(xs)
    if (LOG_SORT_NAMES) java.util.Collections.sort(s)
    for (int i=0; i<s.size(); i+=size) out.add(s.subList(i, Math.min(s.size(), i+size)).join(', '))
    return out
  }

  // Счётчики
  logInfo("Обработано сотрудников: ${processedEmployees} из ${fioList?.size() ?: 0}")
  logInfo("Закрыто задач (разрешен → закрыт): ${tasksClosedTotal}")
  logInfo("Переназначено открытых задач: ${tasksReassignedTotal}")
  logInfo("Лицензий снято (→ notLicensed): ${licensesChanged} (уже были: ${licensesAlreadyNot})")
  logInfo("Архивировано (removed=true): ${archivedOk}")

  // ФИО — только по ключевым кейсам, порциями и с лимитом строк
  if (licenseChangedEmployees && canLog()) {
    logInfo("Лицензия снята у: ${licenseChangedEmployees.size()}")
    chunked(licenseChangedEmployees, LOG_CHUNK_NAMES).each { row -> if (canLog()) logInfo("  ${row}") }
  }
  if (archivedEmployees && canLog()) {
    logInfo("Архивированы: ${archivedEmployees.size()}")
    chunked(archivedEmployees, LOG_CHUNK_NAMES).each { row -> if (canLog()) logInfo("  ${row}") }
  }
  if (licenseFailed && canLog()) {
    logInfo("Не удалось снять лицензию: ${licenseFailed.size()}")
    chunked(licenseFailed, LOG_CHUNK_NAMES).each { row -> if (canLog()) logInfo("  ${row}") }
  }
  if (archiveFailed && canLog()) {
    logInfo("Не архивированы: ${archiveFailed.size()}")
    chunked(archiveFailed, LOG_CHUNK_NAMES).each { row -> if (canLog()) logInfo("  ${row}") }
  }
  if (notFoundEmployees && canLog()) {
    logInfo("Не найдены по ФИО: ${notFoundEmployees.size()}")
    chunked(notFoundEmployees, LOG_CHUNK_NAMES).each { row -> if (canLog()) logInfo("  ${row}") }
  }
  if (noDepartmentEmployees && canLog()) {
    logInfo("Без подразделения (parent): ${noDepartmentEmployees.size()}")
    chunked(noDepartmentEmployees, LOG_CHUNK_NAMES).each { row -> if (canLog()) logInfo("  ${row}") }
  }
  if (timedOutNotProcessed && canLog()) {
    logInfo("Не обработаны по таймауту: ${timedOutNotProcessed.size()}")
    chunked(timedOutNotProcessed, LOG_CHUNK_NAMES).each { row -> if (canLog()) logInfo("  ${row}") }
  }
  if (_lines >= LOG_MAX_LINES) { logInfo("… [сводка сокращена: лимит строк ${LOG_MAX_LINES}]") }
}

/* ═════ Завершение ═════ */
CSV_TEXT = null
fioList?.clear()
System.gc()

return report.toString()