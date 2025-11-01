
def CSV_TEXT = $/
Иванов Иван Иванович
Петров Пётр Петрович
/$

/* === КОНФИГ === */
boolean DRY_RUN = false                      // Для прогона на тесте поставьте true
long    MAX_PROCESSING_TIME_MS = 240000      // 4 минуты (жёсткий таймаут)
int     MAX_EDITS_PER_EMPLOYEE = 2000        // предохранитель на 1 сотрудника
int     MAX_TOTAL_EDITS        = 50000       // общий предохранитель по изменениям

// классы, которые обрабатываем
List<String> CLASSES = ['serviceCall', 'task']

// поля вашей модели
final String RESPONSIBLE_EMPLOYEE_FIELD = 'responsibleEmployee'
final String RESPONSIBLE_OU_FIELD       = 'responsibleOu'   // ответственный отдел (вы просили именно это поле)

// пропуски по статусам (лексика как в v6.0)
Set<String> CLOSED_STATUS_CODES = ['closed','закрыт','закрыто'] as Set
Set<String> SKIP_STATUS_CODES = ([
  'resolved','разрешен','разрешено','разрешён',
  'canceled','cancelled','done','completed','finished','archived'
] as Set) + CLOSED_STATUS_CODES
Set<String> SKIP_STATUS_TITLES = [
  'разрешен','разрешено','разрешён','закрыт','закрыто',
  'отклонен','отклонено','отклонён','выполнен','выполнено',
  'решено','решён','завершен','завершено','завершён',
  'отменен','отменено','отменён','архив'
] as Set

// CSV
char DELIM = ','
int  FIO_COL = 0

/* === СВОДКА ЧЕРЕЗ lines (без logger) === */
int    REPORT_SOFT_LIMIT   = 48000           // защитный софт-лимит, чтобы не ловить persistentcontext 50k
def    lines               = [] as List<String>
int    _len                = 0
def addLine = { String s ->
  if (s == null) s = ""
  if (_len + s.length() + 1 <= REPORT_SOFT_LIMIT) {
    lines << s
    _len += s.length() + 1
  } else if (lines.isEmpty() || !lines[-1].contains("[вывод обрезан]")) {
    lines << "… [вывод обрезан]"
    _len += "… [вывод обрезан]".length() + 1
  }
}
def addList = { String label, List<String> items, int chunk = 25, boolean sortIt = true ->
  int count = items?.size() ?: 0
  addLine("${label}: ${count}")
  if (!items || items.isEmpty()) return
  def arr = sortIt ? new ArrayList(items) : new ArrayList(items)
  if (sortIt) java.util.Collections.sort(arr)
  for (int i=0; i<arr.size(); i+=chunk) {
    int j = Math.min(arr.size(), i+chunk)
    addLine("  " + arr.subList(i, j).join(', '))
  }
}

/* === ВРЕМЯ/ТРАНЗАКЦИИ === */
def startTime = System.currentTimeMillis()
def checkTimeout = { -> (System.currentTimeMillis() - startTime) >= MAX_PROCESSING_TIME_MS }
def inTx = { Closure c ->
  try {
    if (this.metaClass.hasProperty(this,'api') && api?.tx) return api.tx.call { c.call() }
    return c.call()
  } catch (ignored) { return null }
}

/* === CSV → список ФИО (как в v6.0) === */
def splitCsv = { String line ->
  def res=[]; def cur=new StringBuilder(); boolean inQuotes=false
  for (int i=0;i<line.length();i++){
    char ch=line.charAt(i)
    if (ch=='"'){
      if (inQuotes && i+1<line.length() && line.charAt(i+1)=='"'){ cur.append('"'); i++ }
      else inQuotes=!inQuotes
    } else if (ch==DELIM && !inQuotes){
      res.add(cur.toString().trim()); cur.setLength(0)
    } else cur.append(ch)
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
  fioList
}

/* === Поиск сотрудника по ФИО (как в v6.0, логику НЕ меняем) === */
def normalizeFio = { String s ->
  (s ?: '').replace('\u00A0',' ').replaceAll(/\s+/, ' ')
           .replace('ё','е').replace('Ё','Е').trim()
}
def toObj = { any -> try { (any instanceof String) ? utils.get(any) : any } catch (ignored){ any } }
def findEmployeeByFio = { String fioInput ->
  try {
    def fio = normalizeFio(fioInput); if (!fio) return null
    try { def f=utils.find('employee',[title:fio], sp.ignoreCase()); if (f?.size()==1) return toObj(f[0]) } catch(ignored){}
    try { def f=utils.find('employee',[title:op.like("%${fio}%")], sp.ignoreCase()); if (f?.size()==1) return toObj(f[0]) } catch(ignored){}
    def parts = fio.tokenize(' ')
    if (parts.size()>=2){
      try { def f=utils.find('employee',[lastName:parts[0], firstName:parts[1]], sp.ignoreCase()); if (f?.size()==1) return toObj(f[0]) } catch(ignored){}
    }
    null
  } catch (e){ null }
}

/* === Отдел сотрудника (parent) → UUID + объект OU === */
def getEmployeeDepartmentUuid = { emp ->
  try {
    def parent = emp?.parent; if (!parent) return null
    def uuid   = parent?.UUID; if (!uuid) return null
    def s = uuid.toString()
    s.contains('$') ? s : "ou\$${s}"
  } catch (e){ null }
}
def toOuObj = { String depUuid ->
  try {
    if (!depUuid) return null
    def id = depUuid.contains('$') ? depUuid : "ou\$${depUuid}"
    utils.get(id)     // вернуть объект подразделения
  } catch (ignored){ null }
}

/* === Статус объекта === */
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
    [code,title]
  } catch (e){ ['', ''] }
}

/* === равенство OU по UUID (игнор префикса) === */
def sameOu = { String a, String b ->
  if (!a || !b) return false
  def ia = a.contains('$') ? a.split('\\$',2)[1] : a
  def ib = b.contains('$') ? b.split('\\$',2)[1] : b
  ia == ib
}

/* === Снять персонального ответственного, оставить отдел (с верификацией) === */
def removePersonKeepDepartment = { emp, String departmentUuid, List<String> classes ->
  def changed = 0
  def alreadyOk = 0
  def failed  = 0
  int totalEditsLocal = 0

  def ouObj = toOuObj(departmentUuid)

  classes.each { cls ->
    if (checkTimeout()) return [changed:changed, alreadyOk:alreadyOk, failed:failed]
    // только объекты, где именно этот сотрудник является ответственным
    def list = []
    try { list = utils.find(cls, [(RESPONSIBLE_EMPLOYEE_FIELD): emp]) ?: [] } catch(ignored){}

    int perEmp = 0
    list.each { obj ->
      if (checkTimeout()) return [changed:changed, alreadyOk:alreadyOk, failed:failed]
      if (perEmp >= MAX_EDITS_PER_EMPLOYEE) return
      if (totalEditsLocal >= MAX_TOTAL_EDITS) return

      def (code,title) = getStatusInfo(obj)
      if (SKIP_STATUS_CODES.contains(code) || SKIP_STATUS_TITLES.any { title.contains(it) }) return

      def ouNowObj  = obj."${RESPONSIBLE_OU_FIELD}"
      def ouNowUuid = ouNowObj?.UUID?.toString() ?: (ouNowObj instanceof String ? ouNowObj : null)
      def empNow    = obj."${RESPONSIBLE_EMPLOYEE_FIELD}"

      // уже «как надо»: OU совпадает и персонального нет
      if (sameOu(ouNowUuid, departmentUuid) && empNow == null) { alreadyOk++; return }

      try {
        if (!DRY_RUN) {
          if (ouObj == null) { failed++; return }
          inTx { utils.edit(obj, [
            (RESPONSIBLE_OU_FIELD)      : ouObj,   // OU как ОБЪЕКТ (важно для аудита)
            (RESPONSIBLE_EMPLOYEE_FIELD): null
          ]) }
        }

        // верификация
        def fresh = DRY_RUN ? obj : (obj?.UUID ? utils.get(obj.UUID) : obj)
        def empAfter    = fresh."${RESPONSIBLE_EMPLOYEE_FIELD}"
        def ouAfter     = fresh."${RESPONSIBLE_OU_FIELD}"
        def ouAfterUuid = ouAfter?.UUID?.toString() ?: (ouAfter instanceof String ? ouAfter : null)
        boolean ok = (empAfter == null) && sameOu(ouAfterUuid, departmentUuid)

        if (ok) { changed++; perEmp++; totalEditsLocal++ } else { failed++ }
      } catch (e) {
        failed++
      }
    }
  }

  [changed: changed, alreadyOk: alreadyOk, failed: failed]
}

/* === Основной проход === */
def fioList = buildFioList(CSV_TEXT)
if (!fioList || fioList.isEmpty()) {
  addLine("CSV/текст пуст — нет ФИО для обработки.")
  return lines.join('\n')
}

// Сводка
int employeesProcessed = 0
int totalChanged = 0
int totalAlready = 0
int totalFailed  = 0

def notFoundEmployees     = [] as List<String>
def noDepartmentEmployees = [] as List<String>
def changedEmployees      = [] as List<String>
def alreadyEmployees      = [] as List<String>
def failedEmployees       = [] as List<String>
def timedOutNotProcessed  = [] as List<String>

for (int i=0; i<fioList.size(); i++) {
  if (checkTimeout()) { timedOutNotProcessed.addAll(fioList.subList(i, fioList.size())); break }

  def fio = fioList[i]
  def emp = findEmployeeByFio(fio)
  if (!emp) { notFoundEmployees << fio; continue }

  def depUuid = getEmployeeDepartmentUuid(emp)
  if (!depUuid) { noDepartmentEmployees << fio; continue }

  def res = removePersonKeepDepartment(emp, depUuid, CLASSES)
  employeesProcessed++
  totalChanged += res.changed
  totalAlready += res.alreadyOk
  totalFailed  += res.failed

  if      (res.changed > 0) changedEmployees << fio
  else if (res.failed  > 0) failedEmployees  << fio
  else                      alreadyEmployees << fio

  if (checkTimeout()) { timedOutNotProcessed.addAll(fioList.subList(i+1, fioList.size())); break }
}

/* === ИТОГОВАЯ СВОДКА (только через lines) === */
addLine("=== СВОДКА (DRY_RUN=${DRY_RUN ? 'ON' : 'OFF'}) ===")
addLine(String.format("%-36s %s", "Обработано сотрудников:", "${employeesProcessed} из ${fioList.size()}"))
addLine(String.format("%-36s %s", "Изменений применено (сняли ФЛП):", totalChanged))
addLine(String.format("%-36s %s", "Уже было как нужно:",            totalAlready))
addLine(String.format("%-36s %s", "Не удалось применить:",           totalFailed))
addLine("")
addList("С изменениями (ФИО)",            changedEmployees)
addList("Без изменений (и так ок)",       alreadyEmployees)
addList("Не удалось применить (ФИО)",     failedEmployees)
addList("Не найдены по ФИО",              notFoundEmployees)
addList("Без подразделения (parent)",     noDepartmentEmployees)
addList("Не обработаны из-за таймаута",   timedOutNotProcessed)

/* === Завершение === */
CSV_TEXT = null
return lines.join('\n')