INSERT INTO "account"
VALUES (1, 'test@example.com', NULL,
		'$argon2i$v=19$m=65536,t=2,p=1$IOkN/HaqPieB0AFpeyXeHg$A/SVHu3SDhxwvjEyYBUoqLJKwFiE9i4hwGIusxo4k6A',
		'J2UkIM9kCx3JXxrZJ0Wvcw', '2023-09-16 06:25:38', 1, '2023-09-16 06:25:38', NULL);
INSERT INTO "white_list"
VALUES ('test3@example.com', 0, 'Test data');
INSERT INTO "server_config"
VALUES ('whitelist_enabled', 'true', 1704067200);
