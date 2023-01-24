package tasklist

import kotlinx.datetime.*
import java.time.LocalTime
import java.time.format.DateTimeParseException
import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

fun main() {
    Tasks.console()
}

object Tasks {
    private var tasksList = mutableListOf<Task>()
    private var lastId = 0
    private lateinit var lastTask: Task
    var jsonFileName: String = "tasklist.json"

    init {
        load()
    }

    enum class Fields { PRIORITY, DATE, TIME, TASK, }

    enum class Actions(val command: String) {
        ADD("add"), PRINT("print"), END("end"), EDIT("edit"), DELETE("delete"),
    }

    enum class DueTags(val tag: String, val colorCode: ColorCodes) {
        TODAY("T", ColorCodes.YELLOW), INTIME("I", ColorCodes.GREEN), OVERDUE("O", ColorCodes.RED),
    }

    enum class ColorCodes(val code: String) {
        RED("\u001B[101m \u001B[0m"),
        YELLOW("\u001B[103m \u001B[0m"),
        GREEN("\u001B[102m \u001B[0m"),
        BLUE("\u001B[104m \u001B[0m"),
    }

    class LocalDateSerializable() {
        var year: Int = 0
        var month: Int = 0
        var day: Int = 0

        constructor(dateString: String) : this() {
            val localDate = LocalDate.parse(
                dateString.trim().uppercase().split("-").joinToString("-") {
                    it.padStart(2, '0')
                }
            )
            year = localDate.year
            month = localDate.monthNumber
            day = localDate.dayOfMonth
        }

        fun toLocalDate(): LocalDate = LocalDate(year, month, day)

        override fun toString(): String = this.toLocalDate().toString()
    }

    class LocalTimeSerializable() {
        var hours: Int = 0
        var minutes: Int = 0

        constructor(timeString: String) : this() {
            val localTime = LocalTime.parse(
                timeString.trim().split(":").joinToString(":") {
                    it.padStart(2, '0')
                }
            )
            hours = localTime.hour
            minutes = localTime.minute
        }

        fun toLocalTime(): LocalTime = LocalTime.parse(
            "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
        )

        override fun toString(): String {
            return this.toLocalTime().toString()
        }
    }

    data class Task(
        var id: Int, var steps: MutableList<String>, var priority: Priority,
        var localDate: LocalDateSerializable, var localTime: LocalTimeSerializable
    ) {
        enum class Priority(val tag: String, val colorCode: ColorCodes) {
            CRITICAL("C", ColorCodes.RED), HIGH("H", ColorCodes.YELLOW),
            NORMAL("N", ColorCodes.GREEN), LOW("L", ColorCodes.BLUE),
        }
    }

    private fun getJsonTaskAdapter(): JsonAdapter<MutableList<Task>> {
        val moshiBuilder = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val type = Types.newParameterizedType(MutableList::class.java, Task::class.java)
        return moshiBuilder.adapter<MutableList<Task>>(type)
    }

    private fun save() {
        with(File(jsonFileName)) {
            writeText(getJsonTaskAdapter().toJson(tasksList))
        }
    }

    private fun load() {
        val jsonData = with(File(jsonFileName)) {
            if (exists()) readText(Charsets.UTF_8) else return
        }
        tasksList = getJsonTaskAdapter().fromJson(jsonData) ?: mutableListOf()
        lastTask = tasksList.last()
        lastId = lastTask.id
    }

    private fun create(
        description: MutableList<String>,
        priority: Task.Priority,
        localDate: LocalDateSerializable,
        localTime: LocalTimeSerializable
    ) {
        lastTask = Task(++lastId, description, priority, localDate, localTime)
        tasksList.add(lastTask)
    }

    private fun delete(id: Int) {
        for (taskId in id until lastId) {
            tasksList[taskId - 1] = tasksList[taskId]
            tasksList[taskId - 1].id--
        }
        tasksList.removeAt(--lastId)
    }

    private fun edit(id: Int, field: Fields) {
        when (field) {
            Fields.TASK -> {
                tasksList[id - 1].steps = inputSteps()
            }

            Fields.DATE -> {
                tasksList[id - 1].localDate = inputDateDL()
            }

            Fields.TIME -> {
                tasksList[id - 1].localTime = inputTimeDL()
            }

            Fields.PRIORITY -> {
                tasksList[id - 1].priority = inputPriority()
            }
        }
    }

    enum class TableSections(val size: Int, val title: String, val indent: Int) {
        INDEX(4, "N", 1), DATE(12, "Date", 4), TIME(7, "Time", 1),
        Priority(3, "P", 1), DUE(3, "D", 1), TASK(44, "Task", 19),
    }

    private fun sliceTasks(taskStep: String): MutableList<String> {
        var lettersLeft = taskStep.length
        var startIndex = 0
        val result = mutableListOf<String>()
        while (lettersLeft > TableSections.TASK.size) {
            result.add(taskStep.substring(startIndex, startIndex + TableSections.TASK.size))
            startIndex += TableSections.TASK.size
            lettersLeft -= TableSections.TASK.size
        }
        result.add(taskStep.substring(startIndex) + " ".repeat(TableSections.TASK.size - lettersLeft))
        return result
    }

    private fun getFormattedTasksPrintList(): List<String> {
        val result = mutableListOf<String>()
        val (sepHorizontalSymbol, sepVerticalSymbol, horizontalSpacer) = listOf("+", "|", "-")
        val hBorder = sepHorizontalSymbol +
                listOf(
                    TableSections.INDEX, TableSections.DATE, TableSections.TIME,
                    TableSections.Priority, TableSections.DUE, TableSections.TASK
                ).joinToString("") {
                    horizontalSpacer.repeat(it.size) + sepHorizontalSymbol
                }
        val title = sepVerticalSymbol + listOf(
            TableSections.INDEX, TableSections.DATE, TableSections.TIME,
            TableSections.Priority, TableSections.DUE, TableSections.TASK
        ).joinToString("") {
            " ".repeat(it.indent) + it.title + " ".repeat(it.size - it.indent - it.title.length) + sepVerticalSymbol
        }

        result.add(hBorder)
        result.add(title)
        result.add(hBorder)

        for (task in tasksList) {
            val sliced = mutableListOf<String>()
            task.steps.forEach { sliced.addAll(sliceTasks(it)) }
            val taskEntry =
                sepVerticalSymbol + " ${task.id}" + " ".repeat(TableSections.INDEX.size - task.id.toString().length - 1) +
                        sepVerticalSymbol + " ${task.localDate} " +
                        sepVerticalSymbol + " ${task.localTime} " +
                        sepVerticalSymbol + " ${task.priority.colorCode.code} " +
                        sepVerticalSymbol + " ${getDueTag(task).colorCode.code} " +
                        sepVerticalSymbol + sliced[0] + " ".repeat(TableSections.TASK.size - sliced[0].length) +
                        sepVerticalSymbol + "\n" +
                        sliced.filterIndexed { index, _ -> index > 0 }.joinToString("") { it ->
                            sepVerticalSymbol +
                                    listOf(
                                        TableSections.INDEX,
                                        TableSections.DATE,
                                        TableSections.TIME,
                                        TableSections.Priority,
                                        TableSections.DUE
                                    ).joinToString("") { " ".repeat(it.size) + sepVerticalSymbol } +
                                    it + sepVerticalSymbol + "\n"
                        }
            result.add(taskEntry)
            result.add(hBorder)
        }
        return result
    }

    private fun getDueTag(task: Task): DueTags {
        val daysUntil = Clock.System.now().toLocalDateTime(TimeZone.of("UTC+0")).date
            .daysUntil(task.localDate.toLocalDate())
        return if (daysUntil == 0) DueTags.TODAY
        else if (daysUntil > 0) DueTags.INTIME
        else DueTags.OVERDUE
    }

    private fun printTasks(): Boolean {
        if (tasksList.isEmpty()) {
            println("No tasks have been input")
            return false
        }
        println(getFormattedTasksPrintList().joinToString("\n"))
        return true
    }

    private fun inputPriority(): Task.Priority {
        while (true) {
            println("Input the task priority (C, H, N, L):")
            val input = readln().trim().uppercase()
            val priority: Task.Priority? = Task.Priority.values().firstOrNull {
                it.tag.uppercase() == input
            }
            if (priority != null) return priority
        }
    }

    private fun inputDateDL(): LocalDateSerializable {
        while (true) {
            println("Input the date (yyyy-mm-dd):")
            try {
                return LocalDateSerializable(readln())
            } catch (e: IllegalArgumentException) {
                println("The input date is invalid")
            }
        }
    }

    private fun inputTimeDL(): LocalTimeSerializable {
        while (true) {
            println("Input the time (hh:mm):")
            try {
                return LocalTimeSerializable(readln())
            } catch (e: DateTimeParseException) {
                println("The input time is invalid")
            }
        }
    }

    private fun inputTask(lastId: Int): Int {
        while (true) {
            println("Input the task number (1-${lastId}):")
            try {
                val input = readln().toInt()
                if (input !in 1..lastId) throw NumberFormatException()
                return input
            } catch (e: NumberFormatException) {
                println("Invalid task number")
            }
        }
    }

    private fun inputFieldToEdit(): Fields {
        while (true) {
            println("Input a field to edit (priority, date, time, task):")
            try {
                return Fields.valueOf(readln().uppercase())
            } catch (e: IllegalArgumentException) {
                println("Invalid field")
            }
        }
    }

    private fun inputSteps(): MutableList<String> {
        println("Input a new task (enter a blank line to end):")
        val stepsList = mutableListOf<String>()
        while (true) {
            val input = readln().trim()
            if (input.isEmpty()) {
                if (stepsList.isEmpty()) println("The task is blank")
                break
            }
            stepsList.add(input)
        }
        return stepsList
    }

    fun console() {
        var commandExit = false
        while (!commandExit) {
            println("Input an action (add, print, edit, delete, end):")
            when (readln()) {
                Actions.ADD.command -> {
                    val priority: Task.Priority = inputPriority()
                    val dateDL: LocalDateSerializable = inputDateDL()
                    val timeDL: LocalTimeSerializable = inputTimeDL()
                    val stepsList = inputSteps()
                    create(stepsList, priority, dateDL, timeDL)
                }

                Actions.PRINT.command -> {
                    printTasks()
                }

                Actions.END.command -> {
                    println("Tasklist exiting!")
                    save()
                    commandExit = true
                }

                Actions.EDIT.command -> {
                    if (printTasks()) {
                        edit(inputTask(lastId), inputFieldToEdit())
                        println("The task is changed")
                    }
                }

                Actions.DELETE.command -> {
                    if (printTasks()) {
                        delete(inputTask(lastId))
                        print("The task is deleted")
                    }
                }

                else -> println("The input action is invalid")
            }
        }
    }
}