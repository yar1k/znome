$(document).ready(function(){
    
    /* Переменная-флаг для отслеживания того, происходит ли в данный момент ajax-запрос. В самом начале даем ей значение false, т.е. запрос не в процессе выполнения */
    var inProgress = false;
    /* С какой статьи надо делать выборку из базы при ajax-запросе */
    var next = 20;
    var pattern1 = $("#1pattern").text();
    var pattern2 = $("#2pattern").text();
    /* Используйте вариант $('#more').click(function() для того, чтобы дать пользователю возможность управлять процессом, кликая по кнопке "Дальше" под блоком статей (см. файл index.php) */
    $(window).scroll(function() {
	
        /* Если высота окна + высота прокрутки больше или равны высоте всего документа и ajax-запрос в настоящий момент не выполняется, то запускаем ajax-запрос */
        if($(window).scrollTop() + $(window).height() >= $(document).height() - 200 && !inProgress) {
	    
            $.ajax({
		/* адрес файла-обработчика запроса */
		url: '/search/ajax',
		/* метод отправки данных */
		method: 'POST',
		/* данные, которые мы передаем в файл-обработчик */
		data: {"next" : next, "pattern1": pattern1, "pattern2": pattern2},
		/* что нужно сделать до отправки запрса */
		beforeSend: function() {
		    /* меняем значение флага на true, т.е. запрос сейчас в процессе выполнения */
		    inProgress = true;
		}
		/* что нужно сделать по факту выполнения запроса */
            }).done(function(data){
		/* Преобразуем результат, пришедший от обработчика - преобразуем json-строку обратно в массив */
		console.log(data);

		/* Если массив не пуст (т.е. статьи там есть) */
		if (data.length > 0) {
		    
		    /* Делаем проход по каждому результату, оказвашемуся в массиве,
		       где в index попадает индекс текущего элемента массива, а в data - сама статья */
		    $.each(data, function(index, data){
			console.log(data.id);
			/* Отбираем по идентификатору блок со статьями и дозаполняем его новыми данными */
			$("#people").append("<tr><td>"+data.user_id+"</td><td>"+ data.name + "</td><td>"+data.city+"</td><td>"+data.school+"</td><td><a href=\"/admin/user/"+data.user_id+"\"><img src='/assets/images/admin/icn_edit.png'/></a></td><td><a href=\"/admin/user/"+data.user_id+"/delete\"><img src='/assets/images/admin/icn_trash.png'/></a></td></tr>");

		    });
		    
		    /* По факту окончания запроса снова меняем значение флага на false */
		    inProgress = false;
		    // Увеличиваем на 10 порядковый номер статьи, с которой надо начинать выборку из базы
		    next += 20;
		}});
        }
    });
});