package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.Play.current
import models._
import services.EmailService.sendEmail
import akka.util.Crypt.md5
import akka.util.Crypt.sha1

object User extends Controller with Secured {

  // -- Контроллер для пользователей

  // Формы (для входа, регистрации, редактирования профиля и восстановления пароля)
  val loginForm = Form(
    tuple(
      "email" -> email,
      "password" -> nonEmptyText) verifying("Невірна пошта або пароль", result => result match {
      case (email, pass) => mUser.authenticate(email, pass)
    }))

  val registerForm = Form(
    tuple(
      "email" -> email,
      "name" -> nonEmptyText(8, 100),
      "password" -> nonEmptyText(6, 100),
      "confirm" -> nonEmptyText) verifying("Користувач з такою поштою вже зареєстрований", result => result match {
      case (email, name, password, confirm) => mUser.checkUser(email)
    })
  )

  val editForm = Form(
    tuple(
      "email" -> email,
      "name" -> nonEmptyText,
      "password" -> text,
      "newpass" -> text,
      "confirm" -> text,
      "city" -> text,
      "school" -> text,
      "comments" -> text))

  val recoverForm = Form(
    "email" -> email.verifying ("Немає користувача з такою поштою", result => result match {
      case email => !mUser.checkUser(email)
    })
  )

  val peopleForm = Form(
    tuple(
      "name" -> text,
      "city" -> text
    )
  )

  val lessonForm = Form(
    tuple(
      "ukr" -> boolean,
      "mat" -> boolean,
      "ang" -> boolean,
      "ino" -> boolean,
      "fiz" -> boolean,
      "him" -> boolean,
      "bio" -> boolean,
      "geo" -> boolean,
      //История Украины и всемирная история
      "isy" -> boolean,
      "isv" -> boolean,
      "lit" -> boolean,
      "rus" -> boolean
    )
  )
  /*Поиск по людям*/
  def peopleSearch = Action {
    implicit request =>
      peopleForm.bindFromRequest.fold(
        formWithErrors => Redirect(routes.Static.home).flashing("error" -> "Введіть прізвище або оберіть область!"),
        pattern => {
          // Тут будет отсеиванье ненужных людей
	  var users: List[mUser] = List()
          var pattern1 = "%"
          var pattern2 = "%"
	  if(pattern._1 == "" && pattern._2 == ""){
	    users = mProfile.findPattern("%", "%")
	  }else{
	    val name = "%" + pattern._1 + "%"
	    val city = "%" + pattern._2 + "%"
            pattern1 = name
            pattern2 = city
	    users = mProfile.findPattern(name, city)
	  }
	  Ok(views.html.user.search(users, 1, pattern1, pattern2))
        })
  }

  def peopleJson = Action { request =>
    val next = request.body.asFormUrlEncoded.get("next")(0).toString.toInt
    val pattern1 = request.body.asFormUrlEncoded.get("pattern1")(0).toString
    val pattern2 = request.body.asFormUrlEncoded.get("pattern2")(0).toString
//    val next: Long = value.get("next")[0]
    val users = mProfile.findPattern(pattern1, pattern2, next)
    val profiles = for (u <- users) yield mProfile.find(u.id)
    import play.api.libs.json._
    implicit val userFormat = Json.format[mProfile]
    val json_users = Json.toJson(profiles)
    Ok(json_users)
  }

  def search = Action { implicit request =>
    Ok(views.html.user.search(List(), 2, "", ""))
  }
  /**
   * Регистрация
   */

  // Страница регистрации
  def register = Action {
    implicit request =>
      Ok(views.html.user.register(registerForm))
  }

  // Обработка данных, введенных в форму регистрации.
  def createUser = Action {
    implicit request =>
      registerForm.bindFromRequest.fold(
        formWithErrors => BadRequest(views.html.user.register(formWithErrors)),
        user => {
          if (user._3 != user._4) Redirect(routes.User.register).flashing("error" -> "Ви ввели різні паролі")
          else {
            val id = mUser.create(user._1, user._2, user._3)
            sendEmail(user._1,
              s"""
	      <html><head><meta http-equiv=«Content-Type» content="text/html; charset=utf-8"></head><body>
              Вітаю, ${user._2}<br>
              Ви були успішно зареєстровані!<br>
		      <p>
              Ваша пошта: ${user._1}<br>
              Ваш пароль: ${user._3}
		      </p>
              <i>З повагою, Ярослав Круковський.</i>
		      </body>
            """)
            Redirect(routes.User.profile(id)).withSession(Security.username -> id.toString, "id" -> id.toString).flashing(
              "success" -> "Ви були успішно зареєстровані")
		}          
        })
  }

  /**
   * Аутентификация пользователя
   */

  // Отправляет клиенту страницу входа.
  def login = Action {
    implicit request =>
      Ok(views.html.user.login(loginForm))
  }

  // Обработка информации, веденной клиентом на странице входа.
  def authenticate = Action {
    implicit request =>
      loginForm.bindFromRequest.fold(
        formWithErrors => BadRequest(views.html.user.login(formWithErrors)),
        user => {
          val id = mUser.findByEmail(user._1).id
          Redirect(routes.User.profile(id)).withSession(Security.username -> id.toString, "id" -> id.toString).flashing(
            "success" -> "Ви успішно ввійшли")
        })
  }

  // Выход из аккаунта и очистка сессии.
  def logout = Action{
//    Cache.remove("user_cache" + user.id)
    Redirect(routes.Static.home).withNewSession.flashing(
      "success" -> "Ви вийшли з аккаунту.")
  }

  /**
   * Все, что связано с пользователем (профиль, редактирование информации и т.д.)
   */

  //Страница редактирования профиля пользователя.
  def edit = withProfile {
    (user, profile) =>
      implicit request =>
        Ok(views.html.user.edit(gravatarFor(user.email), editForm.fill(user.email, profile.name, "", "", "", profile.city, profile.school, profile.comments), lessonForm))
  }

  // Обработка даных из формы редактирования профиля.
  def changeUser = withProfile { (user, profile) =>
    implicit request =>
      editForm.bindFromRequest.fold(
        formWithErrors => BadRequest(views.html.user.edit(gravatarFor(user.email), formWithErrors, lessonForm)),
        user_form => {
          if(user_form._4 != "" && user_form._5 != "") {
            if(user.pass != mUser.hashPass(user_form._3))
              Redirect(routes.User.edit).flashing("error" -> "Ви неправильно ввели старий пароль")
            else if(user_form._4 != user_form._5)
              Redirect(routes.User.edit).flashing("error" -> "Ви неправильно ввели підтвердження пароля")
            else {
	      val hashedPass = mUser.hashPass(user_form._4)
              mUser.edit(user.id, user_form._1, hashedPass)
	      val cached = cache.Cache.getOrElse("user_cache" + user.id){
		mUser.find(user.id)
	      }
	      cache.Cache.set("user_cache" + user.id, cached.copy(pass = hashedPass), 60*120)
              Redirect(routes.User.profile(user.id)).flashing("success" -> "Пароль був успішно змінений").withSession(Security.username -> user.id.toString, "id" -> (user.id).toString)
            }
          }else{
	    if(user_form._4 != user_form._5 || user_form._3 != "")
	      Redirect(routes.User.edit).flashing("error" -> "Ви не повністю заповнили форму зміни пароля")
	    else{
              mUser.edit(user.id, user_form._1, user.pass)
              mProfile.edit(user.id, user_form._2, user_form._6, user_form._7 , user_form._8)
	      val cached = cache.Cache.getOrElse("user_cache" + user.id){
		mUser.find(user.id)
	      }
              val cached_profile = cache.Cache.getOrElse("profile_cache" + user.id){
                mProfile.find(user.id)
              }
	      cache.Cache.set("user_cache" + user.id, cached.copy(email = user_form._1), 60*120)
              cache.Cache.set("profile_cache" + user.id, cached_profile.copy(name = user_form._2, city = user_form._6, school = user_form._7, comments = user_form._8))
              Redirect(routes.User.profile(user.id)).flashing("success" -> "Профіль був успішно змінений").withSession(Security.username -> user.id.toString, "id" -> (user.id).toString)
	    }
          }
        })
  }

  def changeLesson = withProfile { (user, profile) =>
    implicit request =>
      lessonForm.bindFromRequest.fold(
        formWithErrors => BadRequest(views.html.user.edit(gravatarFor(user.email), editForm.fill(user.email, profile.name, "", "", "", profile.city, profile.school, profile.comments), lessonForm)),
        lessons => {
          val newlist = lessons.productIterator.toList
          val listComp = List(1, 2, 3, 4, 5, 6, 7, 8, 9, "a", "b", "c")
          var string: String = ""
          for(index <- 0 to 11)
            if(newlist(index)==true) string+=listComp(index)
          mProfile.editLessons(profile.user_id, string)
          val cached_profile = cache.Cache.getOrElse("profile_cache" + user.id){
            mProfile.find(user.id)
          }
	  cache.Cache.remove("profile_cache" + user.id)
	  cache.Cache.set("profile_cache" + profile.user_id, cached_profile.copy(lessons = string), 60*120)
          Redirect(routes.User.profile(user.id)).flashing(
            "success" -> "Інформація про предмети була успішно змінена."
          )
	}
      )
    }
  
  // Отображение профиля пользователя.
  def profile(id: Long) = Action {
    implicit request =>
    val user = mUser.find(id)
    val profile = mProfile.find(id)
    val lessList = Lesson.findAll
    val statList: List[microDailyStat] = microDailyStat.getByUserOnly(user.id)
    var typList = List[Int]()
    statList.foreach{x =>
      if(!typList.contains(x.typ))
        typList = typList :+ x.typ
    }
    var temporary = ""
    var properLess = scala.collection.mutable.Map[Int, String]()
    typList.foreach{x =>
      if(x!=0)
        temporary = lessList(x-1).name
      else
        temporary = "Змішане змагання"
      properLess.update(x, temporary)
    }
    Ok(views.html.user.profile(user, profile, gravatarFor(user.email), statList, properLess))
  }

  // Страница восстановления пароля.
  def forgotPassword = Action {
    implicit request =>
      Ok(views.html.user.recover(recoverForm))
  }

  // Письмо пользователю с ссылкой на страницу восстановления пароля.
  def recoverPassword = Action {
    implicit request =>
      recoverForm.bindFromRequest.fold(
        formWithErrors => BadRequest(views.html.user.recover(formWithErrors)),
        email => {
          val user = mUser.findByEmail(email)
          val profile = mProfile.find(user.id)
          // Ссылка генерируется по принципу www.example.com/1/2 , где 1 - почта, а 2 - хешированый пароль
          val href = "http://www." + request.domain + routes.User.changePassword(email, sha1(user.pass))
          sendEmail(user.email,
            s"""<html><head><meta http-equiv="Content-Type" content="text/html; charset=utf-8"></head><body>
	      <p>Шановний ${
    profile.name
    }!</p>

          <p>    Ви отримали цей лист через те, що забули пароль (якщо це не так - видаліть лист).<br>
              Для вас буде згенерований новий пароль. Перейдіть, будь ласка, за посиланням:<br>
		    $href<br>
		    </i>З повагою, Ярослав Круковський.</i>
            """.stripMargin)
          Redirect(routes.Static.home()).flashing(
            "success" -> "Перевірте почтову скриньку. Лист має надійти з хвилини на хвилину.")
        })
  }

  // Если пользователь правильно перешел по ссылке - генерация нового пароля и отправка его по почте
  def changePassword(email: String, hash: String) = Action {
    implicit request =>
    // Отсеивается случай, когда пользователь правильно набрал email, но неправильно - пароль
      try {
        val user = mUser.findByEmail(email)
        val profile = mProfile.find(user.id)
        if (sha1(user.pass) == hash) {
          val newPass = randomPassword
          sendEmail(email,
            s"""<html><head><meta http-equiv=«Content-Type» content=«text/html; charset=utf-8»></head><body>
<p>           Шановний ${
    profile.name
    }!</p>

          <p> Ось нові дані для входу на сайт:<br>

           e-mail: ${
    user.email
    }<br>
           пароль: $newPass<br>

           Ви можете прямо зараз перейти на сайт: http://www.${
     request.domain + routes.Static.home
    }<br></p>

		    <i>З повагою, Ярослав Круковський.</i>
        """.stripMargin)
          mUser.edit(user.id, user.email, mUser.hashPass(newPass))
          Redirect(routes.Static.home()).flashing(
            "success" -> "Новий пароль висланий на вашу поштову скриньку."
          )
        } else
        // Отсеивается случай, когда не email, ни хэш пароля введен неправильно
          NotFound(views.html.errors.onHandlerNotFound(request))
      } catch {
        case _: Throwable => NotFound(views.html.errors.onHandlerNotFound(request))
      }
  }

  // Подготовка к ЗНО
  // Главная страница подготовки
  def preparation = withProfile {
    (user, profile) =>
      implicit request =>
      val lessons = codeToLessonsList(profile.lessons)
      Ok(views.html.user.prep(user, lessons))
  }
  /**
   * Вспомагательные функции
   */
  //Генерация url для Gravatar с использыванием функции md5 библиотеки akka.util.Crypt
  def gravatarFor(email: String) = "http://www.gravatar.com/avatar/" + md5(email) toLowerCase

  def randomPassword: String = {
    randomString("abcdefghijklmnopqrstuvwxyz0123456789")(10)
  }

  // Generate a random string of length n from the given alphabet
  def randomString(alphabet: String)(n: Int): String = {
    val random = new scala.util.Random
    Stream.continually(random.nextInt(alphabet.size)).map(alphabet).take(n).mkString
  }

  def codeToLessons(code: String) = {
    var string: String = ""
    val lessons = Map('1' -> "Українська мова і література", '2' -> "Математика", '3' ->"Англійська мова",
      '4' -> "Іноземна мова", '5' -> "Фізика", '6' -> "Хімія", '7' -> "Біологія", '8' -> "Географія",
      '9' -> "Історія України",  'a' -> "Всесвітня історія", 'b' -> "Всесвітня література", 'c' -> "Російська мова")
    for(char <- code) string += (lessons(char)+". ")
    string
  }

  def codeToLessonsList(code: String) = {
    val lessons = Map('1' -> "Українська мова і література", '2' -> "Математика", '3' ->"Англійська мова",
      '4' -> "Іноземна мова", '5' -> "Фізика", '6' -> "Хімія", '7' -> "Біологія", '8' -> "Географія",
      '9' -> "Історія України",  'a' -> "Всесвітня історія", 'b' -> "Всесвітня література", 'c' -> "Російська мова")
    val less = for(char <- code) yield (lessons(char))
    val list = (for(name <- less) yield Lesson.findByName(name)).toList
    list
  }

  def codeToLessonsMap(code: String) = {
    val lessons = Map('1' -> "Українська мова і література", '2' -> "Математика", '3' ->"Англійська мова",
      '4' -> "Іноземна мова", '5' -> "Фізика", '6' -> "Хімія", '7' -> "Біологія", '8' -> "Географія",
      '9' -> "Історія України",  'a' -> "Всесвітня історія", 'b' -> "Всесвітня література", 'c' -> "Російська мова")
    
  }

  def rankToText(rank: Int) = {
    val ranks = loadXml("public/ranks.xml")
    val result = ranks \ ("r" + rank)
    result.text
  }

  def allRanks = {
    val ranks = loadXml("public/ranks.xml")
    (for (elem <- 1 to 10) yield ranks \ ("r" + elem)).toList
  }

  def loadXml(path: String) = scala.xml.XML.loadFile(path)

}


// Авторизация
trait Secured {

  // Получение email из сессии
  def username(request: RequestHeader) = request.session.get(Security.username)

  // Переадресация пользователя на страничку входа, при попытке зайти на запрещенные страницы.
  private def onUnauthorized(request: RequestHeader) = Results.Redirect(routes.User.login)

  private def onUnauthorizedAdmin(request: RequestHeader) = Results.Redirect(routes.Static.home)

  // Проверка, есть ли email в сессии
  def withAuth(f: => String => Request[AnyContent] => Result) = {
    Security.Authenticated(username, onUnauthorized) {
      user =>
        Action(request => f(user)(request))
    }
  }

  // Предоставляет доступ к текущему пользователю в контроллерах
  def withUser(f: mUser => Request[AnyContent] => Result) = withAuth {
    username =>
      implicit request =>
        Some(mUser.find(username.toLong)).map {
          user => 
            f(user)(request)
        }.getOrElse(onUnauthorized(request))
  }

  def withAdmin(role: Int)(f: mUser => Request[AnyContent] => Result ) = withAuth {
    username => implicit request =>
    if(mUser.find(username.toLong).role < role)
      onUnauthorizedAdmin(request)
    else{
      Some(mUser.find(username.toLong)).map {
        user =>
        f(user)(request)
      }.getOrElse(onUnauthorized(request))
    }
  }

  def withProfile(f: (mUser, mProfile) => Request[AnyContent] => Result) = withUser {
    user =>
    implicit request =>
    Some(mProfile.find(user.id)).map {
      profile =>
      f(user, profile)(request)
    }.getOrElse(onUnauthorized(request))
  }
}
