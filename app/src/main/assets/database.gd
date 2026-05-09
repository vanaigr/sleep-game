extends Node

var db: SQLite

func _ready():
	db = SQLite.new()
	db.path = "user://sqlite.db"
	db.open_db()
	
	db.query_with_bindings('create table if not exists test(id integer primary key)', [])
	db.query_with_bindings('insert into test(id) values(1) on conflict(id) do nothing', [])
   
