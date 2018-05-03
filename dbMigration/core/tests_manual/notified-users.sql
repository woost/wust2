--
-- Data for Name: membership; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY membership (userid, postid) FROM stdin;
2	1
1	3
3	6
4	7
5	2
5	3
\.


--
-- Data for Name: rawconnection; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY rawconnection (sourceid, targetid, label) FROM stdin;
2	1	2
3	1	2
4	2	2
5	2	2
5	3	2
7	4	2
8	5	2
8	6	2
2	7	2
9	3	2
\.


--
-- Data for Name: rawpost; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY rawpost (id, content, isdeleted, author, created, modified) FROM stdin;
1	1	f	1	2018-03-22 14:24:08.168274	2018-03-22 14:24:08.168274
2	2	f	1	2018-03-22 14:24:24.675758	2018-03-22 14:24:24.675758
3	3	f	1	2018-03-22 14:24:32.083777	2018-03-22 14:24:32.083777
4	4	f	1	2018-03-22 14:24:40.665532	2018-03-22 14:24:40.665532
5	5	f	1	2018-03-22 14:24:47.079404	2018-03-22 14:24:47.079404
6	6	f	1	2018-03-22 14:24:55.732919	2018-03-22 14:24:55.732919
7	7	f	1	2018-03-22 14:25:01.289936	2018-03-22 14:25:01.289936
8	8	f	1	2018-03-22 14:25:40.85306	2018-03-22 14:25:40.85306
9	9	f	1	2018-03-22 14:41:11.904888	2018-03-22 14:41:11.904888
\.


--
-- Data for Name: user; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY "user" (id, name, revision, isimplicit) FROM stdin;
cjevou708000116324rjb3glx	anon-cjevou708000116324rjb3glx	0	t
2	2	0	f
1	1	0	f
3	3	0	f
4	4	0	f
5	5	0	f
\.


