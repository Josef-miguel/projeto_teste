@echo off
rem Encerra o MySQL Server (solicita a senha do root)
set /p MYSQL_ROOT_PASSWORD=Senha do root do MySQL: 
"C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqladmin.exe" -u root --password="%MYSQL_ROOT_PASSWORD%" shutdown
set MYSQL_ROOT_PASSWORD=
