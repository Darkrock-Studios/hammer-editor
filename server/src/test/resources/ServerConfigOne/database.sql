INSERT INTO account (id, email, pen_name, password_hash, cipher_secret, created, is_admin, last_sync, bio, email_verified, community_member)
VALUES (1, 'test@example.com', NULL,
		'$argon2i$v=19$m=65536,t=2,p=1$IOkN/HaqPieB0AFpeyXeHg$A/SVHu3SDhxwvjEyYBUoqLJKwFiE9i4hwGIusxo4k6A',
		'J2UkIM9kCx3JXxrZJ0Wvcw', '2023-09-16 06:25:38+00', TRUE, '2023-09-16 06:25:38+00', NULL, FALSE, FALSE);

INSERT INTO white_list (email, date_added, reason)
VALUES ('test3@example.com', to_timestamp(0), 'Test data');
