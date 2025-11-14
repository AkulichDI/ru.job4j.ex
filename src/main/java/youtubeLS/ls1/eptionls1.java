// Обрабатываем закрытые заявки, у которых factSolvedDate старше года,
// помечаем их removed = true пакетами по 1000 штук с логированием.

def today = new Date()
// "Год назад" (примерно, 365 дней)
def oneYearAgo = today - 365

// Базовый фильтр: только закрытые и ещё не удалённые
def filter = [
        state  : 'closed',   // подставь свой код статуса, если отличается
        removed: false
]

// Находим все подходящие заявки
def allClosedNotRemoved = utils.find('serviceCall', filter) ?: []

logger.info("Найдено закрытых и не removed заявок: ${allClosedNotRemoved.size()}")
logger.info("Будем помечать removed=true для заявок с factSolvedDate <= ${oneYearAgo}")

// Отбираем только те, у кого factSolvedDate не пустой и старше года
def candidates = allClosedNotRemoved.findAll { sc ->
    def d = sc.factSolvedDate   // Фактическая дата разрешения запроса
    d != null && d.time <= oneYearAgo.time
}

logger.info("Кандидатов на пометку removed=true (старше года): ${candidates.size()}")

int batchSize = 1000
int processed = 0
int marked    = 0

// Обрабатываем по 1000 заявок
candidates.collate(batchSize).eachWithIndex { batch, idx ->

    logger.info("Старт обработки пакета №${idx + 1}, в пакете ${batch.size()} заявок")

    batch.each { sc ->
        // Ставим признак removed = true
        utils.edit(sc, [removed: true])
        marked++
    }

    processed += batch.size()
    logger.info("Пакет №${idx + 1} обработан. " +
            "Всего обработано: ${processed}, всего помечено removed=true: ${marked}")
}

logger.info("Завершено. Итог: обработано ${processed} заявок, " +
        "помечено removed=true: ${marked}")