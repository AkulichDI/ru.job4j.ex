/* ═════════════════════════════════════════════════════════════════════════════════════
 * ФИНАЛЬНЫЙ КОД v6.0++ (быстрый, без логирования, строгий таймаут 4 минуты)
 * База: ваша v6.0. Сохранена логика поиска сотрудника и его подразделения (parent).
 * Алгоритмы смены ответственного, снятия лицензии и архива — из v6, без лишних пауз.
 * Порядок: сначала "разрешен" → закрыть, потом прочие открытые → переназначить на подразделение.
 * Архивирование: removed = true. Вывод — только возврат сводного отчёта.
 * ═════════════════════════════════════════════════════════════════════════════════════ */

/* ═════ ВСТАВЬТЕ СВОЙ БОЛЬШОЙ СПИСОК/CSV С ФИО ═════ */
def CSV_TEXT = $/
Иванов Иван Иванович
Петров Пётр Петрович
/$

/* ═════ КОНФИГУРАЦИЯ ═════ */
def DRY_RUN = false                           // ⚠️ По умолчанию ВЫПОЛНЯЕТ РЕАЛЬНЫЕ ИЗМЕНЕНИЯ
long MAX_PROCESSING_TIME_MS = 240000          // ⏱️ Ровно 4 минуты и стоп (ретёрн)
int  MAX_EDITS_PER_EMPLOYEE = 1000            // Предохранитель на сотрудника
int  MAX_TOTAL_EDITS = 50000                  // Глобальный предохранитель (на всякий случай)
char DELIM = ','                              // Разделитель CSV
int  FIO_COL = 0                              // Индекс колонки с ФИО

// Классы и атрибуты связей (как в v6 — не меняем)
List<String> CLASSES = ['serviceCall', 'task']
List<String> REL_ATTRS = [
  'responsibleEmployee','executor','assignee','author',
  'clientEmployee','initiator','manager','observer'
]

// Поля для назначения подразделения (как в v6)
List<String> OU_TARGET_FIELDS = ['responsibleOu','ou']

// Множества статусов (лексика как в v6)
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

/* ═════ ВСПОМОГАТЕЛЬНОЕ: ВРЕМЯ, ТРАНЗАКЦИИ, ОТЧЁТ ═════ */
def startTime = System.currentTimeMillis()
def checkTimeout = { -> (System.currentTimeMillis() - startTime) >= MAX_PROCESSING_TIME_MS }

def inTx = { Closure c ->
  try {
    if (this.metaClass.hasProperty(this,'api') && api?.tx) return api.tx.call { c.call() }
    return c.call()
  } catch (ignored) { return null }
}

def lines = [] as List<String>    // Накопитель строк отчёта (НИКАКОГО println!)

/* ═════ ИЗВЛЕЧЕНИЕ ФИО ИЗ БОЛЬШОГО ТЕКСТА/CSV (как в v6) ═════ */
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

/* ═════ ПОИСК СОТРУДНИКА ПО ФИО (как в v6, не меняем) ═════ */
def normalizeFio = { String s ->
  (s ?: '').replace('\u00A0',' ').replaceAll(/\s+/, ' ')
           .replace('ё','е').replace('Ё','Е').trim()
}
def toObj = { any -> try { (any instanceof String) ? utils.get(any) : any } catch (ignored){ any } }

def findEmployeeByFio = { String fioInput ->
  try {
    def fio = normalizeFio(fioInput); if (!fio) return null
    try {
      def found = utils.find('employee',[title:fio], sp.ignoreCase())
      if (found?.size()==1) return toObj(found[0])
    } catch (ignored){}
    try {
      def found = utils.find('employee',[title: op.like("%${fio}%")], sp.ignoreCase())
      if (found?.size()==1) return toObj(found[0])
    } catch (ignored){}
    def parts = fio.tokenize(' ')
    if (parts.size()>=2){
      try {
        def found = utils.find('employee',[lastName:parts[0], firstName:parts[1]], sp.ignoreCase())
        if (found?.size()==1) return toObj(found[0])
      } catch (ignored){}
    }
    return null
  } catch (e){ return null }
}

/* ═════ СТАТУС ОБЪЕКТА ═════ */
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

/* ═════ ПОДРАЗДЕЛЕНИЕ СОТРУДНИКА (parent → ou$ID) — как в v6 ═════ */
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

/* ═════ ПРОВЕРКА/НАЗНАЧЕНИЕ ПОДРАЗДЕЛЕНИЯ ═════ */
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
def tryAssign = { obj, List fields, String targetUuid ->
  for (String field : fields) {
    try {
      if (alreadyAssignedTo(obj, field, targetUuid)) return 'already'
      if (DRY_RUN) return 'assigned'
      inTx { utils.edit(obj, [(field): targetUuid]) }
      return 'assigned'
    } catch (ignored){}
  }
  return 'failed'
}

/* ═════ ЗАКРЫТИЕ "РАЗРЕШЕННЫХ" (алгоритм из v6, без пауз) ═════ */
def tryCloseResolvedTask = { obj ->
  try {
    def (code,title) = getStatusInfo(obj)
    boolean isResolved = CLOSE_STATUS_CODES.contains(code) ||
                         title.contains('разрешен') || title.contains('разрешё')
    if (!isResolved) return false
    if (DRY_RUN) return true
    try { inTx { utils.edit(obj, [status:[code:'closed']]) }; return true } catch (ignored){}
    try { inTx { utils.edit(obj, [state: 'closed']) };      return true } catch (ignored){}
    return false
  } catch (e){ return false }
}

/* ═════ ИЗ v6: ПОИСК ЗАДАЧ ДЛЯ ЗАКРЫТИЯ (узкий: только ответственный/инициатор) ═════ */
def findTasksToClose = { emp ->
  def tasks = []; def seen = new HashSet()
  try {
    CLASSES.each { cls ->
      ['responsibleEmployee','initiator'].each { attr ->
        if (checkTimeout()) return tasks
        try {
          def list = utils.find(cls, [(attr): emp])
          list?.each { o -> if (o?.UUID && seen.add(o.UUID)) tasks.add(o) }
        } catch (ignored){}
      }
    }
  } catch (ignored){}
  return tasks
}

/* ═════ ИЗ v6: ПОИСК ВСЕХ СВЯЗАННЫХ ДЛЯ ПЕРЕНАЗНАЧЕНИЯ ═════ */
def findAllRelatedObjects = { emp ->
  def related = []; def seen = new HashSet()
  CLASSES.each { cls ->
    REL_ATTRS.each { attr ->
      if (checkTimeout()) return related
      try {
        def list = utils.find(cls, [(attr): emp])
        list?.each { o -> if (o?.UUID && seen.add(o.UUID)) related.add(o) }
      } catch (ignored){}
    }
  }
  return related
}

/* ═════ СМЕНА ЛИЦЕНЗИИ (из v6) ═════ */
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
    if (alreadyNot) return [false,true]
    if (DRY_RUN) return [true,false]
    inTx { utils.edit(emp, [license: 'notLicensed']) }
    return [true,false]
  } catch (e){ return [false,false] }
}

/* ═════ АРХИВ (ТОЛЬКО removed = true, как просили) ═════ */
def archiveEmployee = { emp ->
  try {
    if (emp?.removed == true) return true
    if (DRY_RUN) return true
    inTx { utils.edit(emp, [removed: true]) }
    return true
  } catch (e){ return false }
}

/* ═════ ОСНОВНОЙ ЦИКЛ ═════ */
def fioList = buildFioList(CSV_TEXT)
if (!fioList || fioList.isEmpty()) {
  lines << "CSV/текст пуст — нет ФИО для обработки."
  return lines.join('\n')
}

// Сводные счётчики
int processedEmployees = 0
int tasksClosedTotal = 0
int tasksReassignedTotal = 0
int licensesChanged = 0
int licensesAlreadyNot = 0
int archivedOk = 0
int totalEdits = 0

// Отчётные списки по проблемам
def notFoundEmployees = [] as List<String>
def noDepartmentEmployees = [] as List<String>
def licenseFailed = [] as List<String>
def archiveFailed = [] as List<String>
def timedOutNotProcessed = [] as List<String>

for (int i=0; i<fioList.size(); i++) {
  if (checkTimeout()) { timedOutNotProcessed.addAll(fioList.subList(i, fioList.size())); break }

  def fio = fioList[i]
  def emp = findEmployeeByFio(fio)
  if (!emp) { notFoundEmployees << fio; continue }

  def departmentUuid = getEmployeeDepartment(emp)
  if (!departmentUuid) { noDepartmentEmployees << fio; continue }

  // === 1) МАССИВ #1: только "разрешен" — ЗАКРЫВАЕМ ===
  def toClose = findTasksToClose(emp) ?: []
  int closedForEmp = 0
  for (obj in toClose) {
    if (checkTimeout()) { timedOutNotProcessed.addAll(fioList.subList(i+1, fioList.size())); break }
    if (totalEdits >= MAX_TOTAL_EDITS) break
    def (code,title) = getStatusInfo(obj)
    boolean isResolved = CLOSE_STATUS_CODES.contains(code) || title.contains('разрешен') || title.contains('разрешё')
    if (!isResolved) continue
    if (tryCloseResolvedTask(obj)) {
      closedForEmp++; tasksClosedTotal++; totalEdits++
      if (closedForEmp >= MAX_EDITS_PER_EMPLOYEE) break
    }
  }

  // === 2) МАССИВ #2: все прочие открытые — ПЕРЕНАЗНАЧАЕМ ===
  def related = findAllRelatedObjects(emp) ?: []
  int reassignedForEmp = 0
  for (obj in related) {
    if (checkTimeout()) { timedOutNotProcessed.addAll(fioList.subList(i+1, fioList.size())); break }
    if (totalEdits >= MAX_TOTAL_EDITS) break
    def (code,title) = getStatusInfo(obj)
    // Отбрасываем уже закрытые/разрешенные/прочие "пропускные"
    if (SKIP_STATUS_CODES.contains(code) || SKIP_STATUS_TITLES.any { title.contains(it) }) continue
    def res = tryAssign(obj, OU_TARGET_FIELDS, departmentUuid)
    if (res == 'assigned') {
      reassignedForEmp++; tasksReassignedTotal++; totalEdits++
      if (reassignedForEmp >= MAX_EDITS_PER_EMPLOYEE) break
    }
  }

  // === 3) Снятие лицензии ===
  def (changed, alreadyNot) = updateLicense(emp)
  if (changed) licensesChanged++
  if (alreadyNot) licensesAlreadyNot++
  if (!changed && !alreadyNot) licenseFailed << fio

  // === 4) Архивирование через removed = true ===
  def okArch = archiveEmployee(emp)
  if (okArch) archivedOk++ else archiveFailed << fio

  processedEmployees++

  // Мягкая локальная очистка ссылок (без GC в цикле)
  toClose?.clear(); related?.clear()
  toClose = null; related = null; emp = null

  if (checkTimeout()) { timedOutNotProcessed.addAll(fioList.subList(i+1, fioList.size())); break }
}

/* ═════ ИТОГОВЫЙ ОТЧЁТ (ТОЛЬКО СВОДКА, БЕЗ ЛОГОВ) ═════ */
lines << "=== СВОДКА ==="
lines << "Обработано сотрудников: ${processedEmployees} из ${fioList.size()}"
lines << "Закрыто задач (\"разрешен\" → \"закрыт\"): ${tasksClosedTotal}"
lines << "Переназначено открытых задач: ${tasksReassignedTotal}"
lines << "Лицензий снято (→ notLicensed): ${licensesChanged} (уже были notLicensed: ${licensesAlreadyNot})"
lines << "Архивировано (removed=true): ${archivedOk}"

// Обязательные разделы по ошибкам/исключениям
if (!licenseFailed.isEmpty())      lines << "Не удалось снять лицензию: ${licenseFailed.size()} → ${licenseFailed.join(', ')}"
if (!archiveFailed.isEmpty())      lines << "Не удалось архивировать (removed=true): ${archiveFailed.size()} → ${archiveFailed.join(', ')}"
if (!notFoundEmployees.isEmpty())  lines << "Не найдены сотрудники по ФИО: ${notFoundEmployees.size()} → ${notFoundEmployees.join(', ')}"
if (!noDepartmentEmployees.isEmpty()) lines << "Не найдено подразделение (parent) у: ${noDepartmentEmployees.size()} → ${noDepartmentEmployees.join(', ')}"
if (!timedOutNotProcessed.isEmpty()) lines << "Не обработаны из-за таймаута: ${timedOutNotProcessed.size()} → ${timedOutNotProcessed.join(', ')}"

// Мягкая финальная очистка, чтобы не мешать другим процессам
CSV_TEXT = null
fioList?.clear()

System.gc()  // один раз в конце

return lines.join('\n')