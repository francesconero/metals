package tests.mcp

import java.nio.file.Files

import tests.BaseLspSuite

class McpRenameLspSuite extends BaseLspSuite("mcp-rename") with McpTestUtils {

  test("rename-local-variable") {
    cleanWorkspace()
    for {
      _ <- initialize(
        """|/metals.json
           |{"a": {}}
           |/a/src/main/scala/com/example/Foo.scala
           |package com.example
           |
           |object Foo {
           |  def hello(): Unit = {
           |    val message = "hi"
           |    println(message)
           |  }
           |}
           |""".stripMargin
      )
      _ <- server.didOpen("a/src/main/scala/com/example/Foo.scala")
      client <- startMcpServer()
      filePath = server.workspace
        .resolve("a/src/main/scala/com/example/Foo.scala")
        .toString
      // "message" is at line 4 (0-based), character 8
      result <- client.rename(
        filePath,
        line = 4,
        character = 8,
        newName = "greeting",
      )
      _ = assertNoDiff(result, "Successfully renamed symbol to 'greeting'")
      content = server.buffers
        .get(workspace.resolve("a/src/main/scala/com/example/Foo.scala"))
        .mkString
      _ = assertNoDiff(
        content,
        """|package com.example
           |
           |object Foo {
           |  def hello(): Unit = {
           |    val greeting = "hi"
           |    println(greeting)
           |  }
           |}
           |""".stripMargin,
      )
      _ <- client.shutdown()
    } yield ()
  }

  test("rename-across-files") {
    cleanWorkspace()
    for {
      _ <- initialize(
        """|/metals.json
           |{"a": {}}
           |/a/src/main/scala/com/example/MyClass.scala
           |package com.example
           |
           |class MyClass {
           |  def greet(): String = "hello"
           |}
           |/a/src/main/scala/com/example/Main.scala
           |package com.example
           |
           |object Main {
           |  def run(): Unit = {
           |    val c = new MyClass()
           |    println(c.greet())
           |  }
           |}
           |""".stripMargin
      )
      _ <- server.didOpen("a/src/main/scala/com/example/MyClass.scala")
      _ <- server.didOpen("a/src/main/scala/com/example/Main.scala")
      client <- startMcpServer()
      filePath = server.workspace
        .resolve("a/src/main/scala/com/example/MyClass.scala")
        .toString
      // "greet" is at line 3 (0-based), character 6
      result <- client.rename(
        filePath,
        line = 3,
        character = 6,
        newName = "sayHello",
      )
      _ = assertNoDiff(result, "Successfully renamed symbol to 'sayHello'")
      definitionContent = server.buffers
        .get(workspace.resolve("a/src/main/scala/com/example/MyClass.scala"))
        .mkString
      _ = assertNoDiff(
        definitionContent,
        """|package com.example
           |
           |class MyClass {
           |  def sayHello(): String = "hello"
           |}
           |""".stripMargin,
      )
      usageContent = server.buffers
        .get(workspace.resolve("a/src/main/scala/com/example/Main.scala"))
        .mkString
      _ = assertNoDiff(
        usageContent,
        """|package com.example
           |
           |object Main {
           |  def run(): Unit = {
           |    val c = new MyClass()
           |    println(c.sayHello())
           |  }
           |}
           |""".stripMargin,
      )
      _ <- client.shutdown()
    } yield ()
  }

  test("rename-top-level-class-renames-file") {
    cleanWorkspace()
    for {
      _ <- initialize(
        """|/metals.json
           |{"a": {}}
           |/a/src/main/scala/com/example/Foo.scala
           |package com.example
           |
           |class Foo {
           |  def bar(): Unit = ()
           |}
           |/a/src/main/scala/com/example/Main.scala
           |package com.example
           |
           |object Main {
           |  def run(): Unit = new Foo().bar()
           |}
           |""".stripMargin
      )
      _ <- server.didOpen("a/src/main/scala/com/example/Foo.scala")
      _ <- server.didOpen("a/src/main/scala/com/example/Main.scala")
      client <- startMcpServer()
      fooPath = server.workspace.resolve(
        "a/src/main/scala/com/example/Foo.scala"
      )
      bazPath = server.workspace.resolve(
        "a/src/main/scala/com/example/Baz.scala"
      )
      // "Foo" is at line 2 (0-based), character 6
      result <- client.rename(
        fooPath.toString,
        line = 2,
        character = 6,
        newName = "Baz",
      )
      _ = assertNoDiff(result, "Successfully renamed symbol to 'Baz'")
      // Foo.scala should be gone, Baz.scala should exist
      _ = assert(
        !Files.exists(fooPath.toNIO),
        "Foo.scala should have been renamed away",
      )
      _ = assert(
        Files.exists(bazPath.toNIO),
        "Baz.scala should exist after rename",
      )
      bazContent = server.buffers.get(bazPath).mkString
      _ = assertNoDiff(
        bazContent,
        """|package com.example
           |
           |class Baz {
           |  def bar(): Unit = ()
           |}
           |""".stripMargin,
      )
      mainContent = server.buffers
        .get(workspace.resolve("a/src/main/scala/com/example/Main.scala"))
        .mkString
      _ = assertNoDiff(
        mainContent,
        """|package com.example
           |
           |object Main {
           |  def run(): Unit = new Baz().bar()
           |}
           |""".stripMargin,
      )
      _ <- client.shutdown()
    } yield ()
  }

  test("rename-non-existent-file") {
    cleanWorkspace()
    for {
      _ <- initialize(
        """|/metals.json
           |{"a": {}}
           |""".stripMargin
      )
      client <- startMcpServer()
      result <- client.rename(
        server.workspace
          .resolve("a/src/main/scala/com/example/NonExistent.scala")
          .toString,
        line = 0,
        character = 0,
        newName = "foo",
      )
      _ = assert(
        result.contains("Error: File not found"),
        s"Expected error message for non-existent file, got: $result",
      )
      _ <- client.shutdown()
    } yield ()
  }

  test("rename-no-symbol-at-position") {
    cleanWorkspace()
    for {
      _ <- initialize(
        """|/metals.json
           |{"a": {}}
           |/a/src/main/scala/com/example/Empty.scala
           |package com.example
           |
           |object Empty
           |""".stripMargin
      )
      _ <- server.didOpen("a/src/main/scala/com/example/Empty.scala")
      client <- startMcpServer()
      filePath = server.workspace
        .resolve("a/src/main/scala/com/example/Empty.scala")
        .toString
      // line 1 is a blank line — no symbol there
      result <- client.rename(
        filePath,
        line = 1,
        character = 0,
        newName = "foo",
      )
      _ = assert(
        result.contains("No renameable symbol found"),
        s"Expected no-symbol message, got: $result",
      )
      _ <- client.shutdown()
    } yield ()
  }
}
