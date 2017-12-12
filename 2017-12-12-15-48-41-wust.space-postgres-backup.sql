--
-- PostgreSQL database dump
--

-- Dumped from database version 9.6.2
-- Dumped by pg_dump version 9.6.2

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET client_min_messages = warning;
SET row_security = off;

SET search_path = public, pg_catalog;

ALTER TABLE IF EXISTS ONLY public.membership DROP CONSTRAINT IF EXISTS usergroupmember_userid_fkey;
ALTER TABLE IF EXISTS ONLY public.membership DROP CONSTRAINT IF EXISTS usergroupmember_groupid_fkey;
ALTER TABLE IF EXISTS ONLY public.groupinvite DROP CONSTRAINT IF EXISTS usergroupinvite_groupid_fkey;
ALTER TABLE IF EXISTS ONLY public.password DROP CONSTRAINT IF EXISTS password_id_fkey;
ALTER TABLE IF EXISTS ONLY public.ownership DROP CONSTRAINT IF EXISTS ownership_postid_fkey;
ALTER TABLE IF EXISTS ONLY public.ownership DROP CONSTRAINT IF EXISTS ownership_groupid_fkey;
ALTER TABLE IF EXISTS ONLY public.containment DROP CONSTRAINT IF EXISTS containment_parentid_fkey;
ALTER TABLE IF EXISTS ONLY public.containment DROP CONSTRAINT IF EXISTS containment_childid_fkey;
ALTER TABLE IF EXISTS ONLY public.connection DROP CONSTRAINT IF EXISTS connection_targetid_fkey;
ALTER TABLE IF EXISTS ONLY public.connection DROP CONSTRAINT IF EXISTS connection_sourceid_fkey;
DROP INDEX IF EXISTS public.usergroupmember_userid_idx;
DROP INDEX IF EXISTS public.usergroupmember_groupid_idx;
DROP INDEX IF EXISTS public.usergroupinvite_token_idx;
DROP INDEX IF EXISTS public.schema_version_s_idx;
DROP INDEX IF EXISTS public.rawpost_isdeleted_idx;
DROP INDEX IF EXISTS public.ownership_postid_idx;
DROP INDEX IF EXISTS public.ownership_groupid_idx;
ALTER TABLE IF EXISTS ONLY public.membership DROP CONSTRAINT IF EXISTS usergroupmember_groupid_userid_key;
ALTER TABLE IF EXISTS ONLY public.groupinvite DROP CONSTRAINT IF EXISTS usergroupinvite_token_key;
ALTER TABLE IF EXISTS ONLY public.groupinvite DROP CONSTRAINT IF EXISTS usergroupinvite_pkey;
ALTER TABLE IF EXISTS ONLY public.usergroup DROP CONSTRAINT IF EXISTS usergroup_pkey;
ALTER TABLE IF EXISTS ONLY public."user" DROP CONSTRAINT IF EXISTS user_pkey;
ALTER TABLE IF EXISTS ONLY public."user" DROP CONSTRAINT IF EXISTS user_name_key;
ALTER TABLE IF EXISTS ONLY public.schema_version DROP CONSTRAINT IF EXISTS schema_version_pk;
ALTER TABLE IF EXISTS ONLY public.rawpost DROP CONSTRAINT IF EXISTS post_pkey;
ALTER TABLE IF EXISTS ONLY public.password DROP CONSTRAINT IF EXISTS password_pkey;
ALTER TABLE IF EXISTS ONLY public.ownership DROP CONSTRAINT IF EXISTS ownership_postid_groupid_key;
ALTER TABLE IF EXISTS ONLY public.containment DROP CONSTRAINT IF EXISTS containment_pkey;
ALTER TABLE IF EXISTS ONLY public.connection DROP CONSTRAINT IF EXISTS connection_pkey;
ALTER TABLE IF EXISTS public.usergroup ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public."user" ALTER COLUMN id DROP DEFAULT;
DROP SEQUENCE IF EXISTS public.usergroup_id_seq;
DROP TABLE IF EXISTS public.usergroup;
DROP SEQUENCE IF EXISTS public.user_id_seq;
DROP TABLE IF EXISTS public."user";
DROP TABLE IF EXISTS public.schema_version;
DROP VIEW IF EXISTS public.post;
DROP TABLE IF EXISTS public.rawpost;
DROP TABLE IF EXISTS public.password;
DROP TABLE IF EXISTS public.ownership;
DROP TABLE IF EXISTS public.membership;
DROP TABLE IF EXISTS public.groupinvite;
DROP TABLE IF EXISTS public.containment;
DROP TABLE IF EXISTS public.connection;
DROP FUNCTION IF EXISTS public.graph_component(start integer);
DROP EXTENSION IF EXISTS plpgsql;
DROP SCHEMA IF EXISTS public;
--
-- Name: public; Type: SCHEMA; Schema: -; Owner: postgres
--

CREATE SCHEMA public;


ALTER SCHEMA public OWNER TO postgres;

--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: postgres
--

COMMENT ON SCHEMA public IS 'standard public schema';


--
-- Name: plpgsql; Type: EXTENSION; Schema: -; Owner: 
--

CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;


--
-- Name: EXTENSION plpgsql; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';


SET search_path = public, pg_catalog;

--
-- Name: graph_component(integer); Type: FUNCTION; Schema: public; Owner: wust
--

CREATE FUNCTION graph_component(start integer) RETURNS SETOF integer
    LANGUAGE plpgsql
    AS $$
declare
    queue Integer[] := array[start];
begin
    create temporary table visited (id integer NOT NULL) on commit drop;
    create unique index on visited (id);

    WHILE array_length(queue,1) > 0 LOOP
        insert into visited (select unnest(queue)) on conflict do nothing;
        queue := array(
            select targetId
            from (select unnest(queue) as id) as q
            join _incidence on sourceId = q.id
            left outer join visited on targetId = visited.id
            where visited.id is NULL
        );
    END LOOP;
    return query (select id from visited);
end;
$$;


ALTER FUNCTION public.graph_component(start integer) OWNER TO wust;

SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: connection; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE connection (
    sourceid character varying(36) NOT NULL,
    targetid character varying(36) NOT NULL,
    CONSTRAINT selfloop CHECK (((sourceid)::text <> (targetid)::text))
);


ALTER TABLE connection OWNER TO wust;

--
-- Name: containment; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE containment (
    parentid character varying(36) NOT NULL,
    childid character varying(36) NOT NULL,
    CONSTRAINT selfloop CHECK (((parentid)::text <> (childid)::text))
);


ALTER TABLE containment OWNER TO wust;

--
-- Name: groupinvite; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE groupinvite (
    groupid integer NOT NULL,
    token text NOT NULL
);


ALTER TABLE groupinvite OWNER TO wust;

--
-- Name: membership; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE membership (
    groupid integer NOT NULL,
    userid integer NOT NULL
);


ALTER TABLE membership OWNER TO wust;

--
-- Name: ownership; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE ownership (
    postid character varying(36) NOT NULL,
    groupid integer NOT NULL
);


ALTER TABLE ownership OWNER TO wust;

--
-- Name: password; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE password (
    id integer NOT NULL,
    digest bytea NOT NULL
);


ALTER TABLE password OWNER TO wust;

--
-- Name: rawpost; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE rawpost (
    id character varying(36) NOT NULL,
    title text NOT NULL,
    isdeleted boolean DEFAULT false NOT NULL
);


ALTER TABLE rawpost OWNER TO wust;

--
-- Name: post; Type: VIEW; Schema: public; Owner: wust
--

CREATE VIEW post AS
 SELECT rawpost.id,
    rawpost.title
   FROM rawpost
  WHERE (rawpost.isdeleted = false);


ALTER TABLE post OWNER TO wust;

--
-- Name: schema_version; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE schema_version (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


ALTER TABLE schema_version OWNER TO wust;

--
-- Name: user; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE "user" (
    id integer NOT NULL,
    name text NOT NULL,
    revision integer DEFAULT 0 NOT NULL,
    isimplicit boolean DEFAULT false NOT NULL
);


ALTER TABLE "user" OWNER TO wust;

--
-- Name: user_id_seq; Type: SEQUENCE; Schema: public; Owner: wust
--

CREATE SEQUENCE user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE user_id_seq OWNER TO wust;

--
-- Name: user_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: wust
--

ALTER SEQUENCE user_id_seq OWNED BY "user".id;


--
-- Name: usergroup; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE usergroup (
    id integer NOT NULL
);


ALTER TABLE usergroup OWNER TO wust;

--
-- Name: usergroup_id_seq; Type: SEQUENCE; Schema: public; Owner: wust
--

CREATE SEQUENCE usergroup_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE usergroup_id_seq OWNER TO wust;

--
-- Name: usergroup_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: wust
--

ALTER SEQUENCE usergroup_id_seq OWNED BY usergroup.id;


--
-- Name: user id; Type: DEFAULT; Schema: public; Owner: wust
--

ALTER TABLE ONLY "user" ALTER COLUMN id SET DEFAULT nextval('user_id_seq'::regclass);


--
-- Name: usergroup id; Type: DEFAULT; Schema: public; Owner: wust
--

ALTER TABLE ONLY usergroup ALTER COLUMN id SET DEFAULT nextval('usergroup_id_seq'::regclass);


--
-- Data for Name: connection; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY connection (sourceid, targetid) FROM stdin;
822	769
904	926
992	879
1386	1383
286	208
286	210
1194	1175
1468	1371
1470	1461
1493	1478
1521	1516
1524	1516
501	414
1587	1585
1636	1603
1700	1698
1704	1713
1750	1724
1833	1698
1841	1735
1838	1724
1752	1761
1882	1761
1882	1738
532	414
1914	1887
1940	1750
2006	1708
422	417
1524	1133
758	757
822	744
926	901
930	926
1049	1041
1145	1143
1166	1145
1463	1371
1496	1470
1511	1404
1591	1585
1621	1607
1465	1131
1509	1131
1402	1131
1721	1698
1758	1761
1754	1761
1794	1724
1747	1761
1847	1724
1908	1887
1831	1774
1945	1750
1952	1750
1950	1708
1973	1750
2011	1750
2039	1700
2160	1847
803	795
933	930
1373	1371
1025	1006
1151	1149
1171	1143
1143	1141
1502	1470
1554	1383
1504	1465
1474	1465
1595	1585
1371	1129
1711	1724
1774	1738
1768	1761
1855	1761
1860	1708
1870	1747
1716	1724
1958	1708
2046	1750
2062	1750
2083	1750
2075	1750
725	723
876	874
919	912
973	971
975	973
977	975
1171	1149
1435	1383
1445	1420
1490	1478
1548	1405
1507	1465
1626	1603
1452	1131
1735	1698
1713	1729
1844	1732
1818	1729
1935	1750
1935	1724
1978	1750
2004	1708
2017	1750
2029	1750
2077	1750
2370	2372
337	208
879	876
795	794
797	794
896	894
800	797
805	795
809	807
1378	1376
1381	1383
1476	1461
1045	1043
1179	1177
1209	1207
1599	1585
1603	1585
1420	1402
1516	1133
1729	1732
1782	1756
1794	1732
1822	1698
1870	1724
1876	1724
1826	1870
1876	1831
1884	1732
1898	1732
2069	1750
1698	1132
2226	2222
2274	2222
2277	2226
2281	2238
2366	2368
2368	2370
734	727
807	794
1410	1405
1155	1151
1185	1179
1196	1194
1203	1201
1175	1141
1530	1470
1527	1516
1470	1132
1713	1732
1743	1761
1797	1761
1800	1724
1810	1794
1800	1743
1841	1732
1925	1750
1984	1708
1994	1708
2022	1708
2034	1708
2321	885
2377	756
916	914
979	977
1219	1217
1412	1376
1422	1412
1416	1412
1400	1405
1438	1420
1418	1405
1457	1376
1481	1376
1483	1468
1487	1478
1534	1404
1537	1468
1607	1585
1611	1607
1616	1607
1631	1603
1405	1132
1454	1402
1738	1724
1790	1761
1887	1732
1860	1887
1961	1750
1999	1750
2051	1750
2056	1750
397	599
887	876
988	986
1037	1033
1160	1155
1183	1179
694	692
700	699
1518	1516
1414	1402
1408	1402
1376	1133
1708	1698
1756	1738
1782	1711
1831	1800
1814	1761
1930	1750
1968	1708
1966	1708
1986	1750
2088	1750
2095	1750
1838	1750
cj3pzm2mn00013k5kje2qc99s	821
cj3pzm56600023k5keb17x9za	821
cj3pzm6ut00033k5kln899ae7	821
cj3pzm79d00043k5km2l2pwc7	821
cj3pzm8sl00053k5k24mo5zwx	821
cj3pzm9a500063k5km8k1b1w7	821
cj3pzmarc00073k5k1qg1doml	821
cj3pzmbc700083k5k59aahdsp	821
cj3pzmbrp00093k5k37vdw1z9	821
cj3pzmcdh000a3k5kqy2dxp2w	821
387	268
208	337
cj3vvv7kn00032z5x8kp51tix	cj3vvriki00012z5xrg9waq5j
cj3vvvtjw00042z5x2wcbnjf8	cj3vvuw9i0000265e5rxc30e1
293	387
cj3stvwph00043i60vbv4p8pr	cj3stulfr00023i60naiq4oba
cj3stvc9y00033i60eu00ymy1	cj3stulfr00023i60naiq4oba
cj3su087a000a3i60qifunrxj	cj3stzs4v00093i60nmjofb7t
cj3stwxsj00053i60her1775v	cj3stvc9y00033i60eu00ymy1
cj3u3gnxz00083k5koj4pickz	cj3u3ghoe00073k5k9o3nvir4
cj3u3kd78000e3k5kz4q2h42v	cj3u3h2un00093k5k0uhyaywd
cj3u3kd78000e3k5kz4q2h42v	cj3u3hpfi000a3k5k8cfa5usv
cj3u3kd78000e3k5kz4q2h42v	cj3u3news000k3k5krbx24qi5
cj3x4xj8j00013k5np9qztf3h	cj3x4uzgu00073k5kya5eh586
cj3ws75bu00002y5lk8jpta0u	cj3x7zqqt00002y5o2rdc4u0u
cj3y9thds00072z633owpf7nw	cj3y9tnle00082z63ddzhqaxx
cj3y9tcba00062z63reun8i8b	cj3y9thds00072z633owpf7nw
cj3y9t79t00052z63wx43hmds	cj3y9tcba00062z63reun8i8b
cj3ya6mxd00092z63ublkrpst	cj3ya6mfg0009315ozjq4e2gf
cj3y9s2qh00022z63r1tvww4d	cj3ycqs6q0003315o0shc3va8
cj3ycszf00005315ov1d6kvdu	cj3ya6mxd00092z63ublkrpst
cj3y9vpyt0003315oe0tn5y6n	cj3ycszf00005315ov1d6kvdu
cj3yd26sa0007315ofrkz826z	cj3ydfq4h00052z637agduxgr
cj3u81m7100023w5dxfev62db	cj3u80mvu00003w5dqsgfc6q9
cj3yjo2qi0002315od9o38swp	cj3yjoenn00012z63k96z76yj
cj3ykyqk300002z63y0ytul5d	cj3ykyzke0002315oxxufn80x
cj3yoij0b0009315opzcm7yig	cj3yoicsf0007315o9sl0vb7c
cj3u81m7100023w5dxfev62db	cj3su3phl000e3i6035mr5l6h
cj3u82fs100033w5dwix8lv0m	cj3u81m7100023w5dxfev62db
cj3uaytm500025t5c3p753gcj	cj3uayqsh00015t5c5a50wxw4
cj3yoxnbb00032b5m6xn80z12	cj3yoxkdp00022b5mi9cdiws3
cj3ugdqq4000j2z5kgaemrdl1	cj3uge7b8000k2z5k5id7h1ui
cj3uge7b8000k2z5k5id7h1ui	cj3uh1ky7000p2z5krbssybae
cj3ugdqkn000i2z5klqhs6cx3	cj3ugdizm000e2z5kczg5j8ct
cj3ugdizm000e2z5kczg5j8ct	cj3uh1ky7000p2z5krbssybae
cj3uh1ky7000p2z5krbssybae	cj3ugdqkn000i2z5klqhs6cx3
cj3ugdizm000e2z5kczg5j8ct	cj3ugdq09000g2z5ktnzev7jf
cj3ugdizm000e2z5kczg5j8ct	cj3ug9g9u000b2z5kleq0bmhy
cj3ug9g9u000b2z5kleq0bmhy	cj3ug9zm2000d2z5kpia2c70x
cj3ug9g9u000b2z5kleq0bmhy	cj3ug9vq9000c2z5kdglisto7
cj3ug9g9u000b2z5kleq0bmhy	cj3ugdnkz000f2z5k6glg8qzk
cj3ug9g9u000b2z5kleq0bmhy	cj3ugdq9m000h2z5krlb1pceq
cj3umlabf00002z5k2phb1xic	cj3px5mil00002z60ybys832b
cj3px5vyq000e315l7dhyyc34	cj3px5mil00002z60ybys832b
cj3ypduwk000o315o223istw5	cj3ypdo95000n315onwovsrad
cj3ypdzl8000p315o63tu2rjh	cj3ypdo95000n315onwovsrad
cj3z1badj00042y5o7x4tffag	cj3z1bo4300052y5ojmmpa60e
cj3z1d5e700072y5o1vv10juw	cj3z1dmr100082y5oayyzfvl7
cj3z1dmr100082y5oayyzfvl7	cj3z1e0vm00092y5obuks7pl0
821	cj41z71pt00021w5wzf0mts6v
cj41z7jq600071w5wy1agk0k8	cj41z8aov000i1w5wd0yzrjhw
153	cj3yasiso00032d5mx3g4ifw7
cj3yay9eg00042d5mrxqt9giu	cj3yasiso00032d5mx3g4ifw7
933	cj3x16hwe00002y5l0dffj95v
2281	cj3x5tb2200012y5ov5ncedwt
cj3y9sw0b00042z63ptf0s41k	cj3y9t79t00052z63wx43hmds
cj3y9rx1500012z63jv6actr6	cj3y9s2qh00022z63r1tvww4d
cj3ya6mfg0009315ozjq4e2gf	cj3y9rx1500012z63jv6actr6
cj3y9vu2z0004315og00g6y0e	cj3ya6mxd00092z63ublkrpst
cj3y9xvbx0008315onn0jdyia	cj3ya6mfg0009315ozjq4e2gf
cj3y9vzut0005315ocykl4l8j	cj3y9rx1500012z63jv6actr6
cj3y9vzut0005315ocykl4l8j	cj3y9s2qh00022z63r1tvww4d
cj3yai9gx0000315owln7e9b1	cj3ye4gcs0000315ouqy8i3p9
cj3ye4gcs0000315ouqy8i3p9	cj3ye4vql0001315orhzrefo0
cj3ye4vql0001315orhzrefo0	cj3ya7561000a315ohp0dibzt
cj3ya6mxd00092z63ublkrpst	cj3yd26sa0007315ofrkz826z
cj3yjl9ih0000315okm62gk1v	cj3yjlioh00002z63si2p5gj2
cj3vvqv1q00013i5x1pv6w796	cj3vvr8x900023i5x9jcfd9zr
cj3styv1d00083i60iz4375ka	cj3sty9gh00073i60cr24n6sr
cj3su4et4000f3i60e3wu4x9w	cj3su0jye000b3i607noyybc7
cj3su3phl000e3i6035mr5l6h	cj3su4et4000f3i60e3wu4x9w
cj3vvvr2f00043i5xdcdfxyuz	cj3vvw8uu00053i5x3djwsp3s
cj3vvrx5g00033i5xt3pobe8v	cj3vvprwg00003i5x4lpggym4
cj3sty9gh00073i60cr24n6sr	cj3vvprwg00003i5x4lpggym4
cj3vvr8x900023i5x9jcfd9zr	cj3vvrx5g00033i5xt3pobe8v
cj3stwxsj00053i60her1775v	cj3styv1d00083i60iz4375ka
cj3su0jye000b3i607noyybc7	cj3su087a000a3i60qifunrxj
cj3su0v9h000c3i60ajxotps5	cj3su164l000d3i60dt0srq97
cj3su164l000d3i60dt0srq97	cj3stwxsj00053i60her1775v
cj3su087a000a3i60qifunrxj	cj3su0v9h000c3i60ajxotps5
cj3ybdlro00052z631bc9o5u0	cj3ykgwdq0000315ongzowvwb
cj3ykygne0000315o8olggsl3	cj3ykyqk300002z63y0ytul5d
cj3yl0jwa0003315o2kmz04a4	cj3yl0swu00012z634bm7xq5e
cj3yl0swu00012z634bm7xq5e	cj3yl109s0004315ok3xv68fv
cj3yoiyp900002b5mnr97ka18	cj3yojrmk000c315op7d3rtv2
cj3youmik0006315oxeo92spt	cj3youfci0005315owjqlj8o4
712	1082
cj3ypetn600072b5mzmimh6yu	cj3ypemy200062b5m1xez9fvb
cj3z1aign00022y5oqgwmizia	cj3z1badj00042y5o7x4tffag
cj3z1bo4300052y5ojmmpa60e	cj3z1c2w900062y5onte6yzwg
cj3z1c2w900062y5onte6yzwg	cj3z1d5e700072y5o1vv10juw
cj3vw56gl00003i5xex5h5kxf	cj3z2uuvx00002y5oeq96lva6
cj41xve5g0006315ocmvbb5l6	cj41y9u12000b315oxw1beii6
cj41z7kp300081w5w4vke15b3	cj41z7jq600071w5wy1agk0k8
812	768
cj3yasiso00032d5mx3g4ifw7	cj3yay9eg00042d5mrxqt9giu
cj3ya6mxd00092z63ublkrpst	cj41xv72q0001315ov746a0i8
cj41zoktl000c2y5omqj61qb1	cj441o5it00002z5n3u00e75s
cj444ud1f000j3k5n27rhhohz	cj444psd400023k5nmdtplsu7
cj444uuki000n3k5ntbecxjqv	cj444u6ub000i3k5nubryy020
cj444utdf000m3k5ne21r895r	cj444u6ub000i3k5nubryy020
cj444trnv000h3k5nwsnyeiyy	cj444u6ub000i3k5nubryy020
cj444us42000l3k5n5f3mmgr3	cj444trnv000h3k5nwsnyeiyy
cj444uywh000p3k5nk4gyrkt7	cj444u6ub000i3k5nubryy020
cj444uxia000o3k5n9crfym40	cj444trnv000h3k5nwsnyeiyy
cj445l8ox000z3k5nslllxgzw	cj444trnv000h3k5nwsnyeiyy
cj444trnv000h3k5nwsnyeiyy	cj445li6p00103k5nij3bqzw2
cj445vjcq001e3k5nho1bx9fm	cj445v8ga001d3k5n7ve4v6qb
cj4465fbx001t3k5nlw25s3ji	cj446xymg00213k5nmuwu82jj
cj446u37y001x3k5nk9ssylw2	cj446xymg00213k5nmuwu82jj
cj446u37y001x3k5nk9ssylw2	cj446u0ty001w3k5n48m8nxvx
cj3pzm9a500063k5km8k1b1w7	cj41z7aqj00061w5wdepdt2nw
cj41zoh8w000a2y5o3egljeuf	cj41z7aqj00061w5wdepdt2nw
cj41zoj1e000b2y5oa5rroymg	cj41z7aqj00061w5wdepdt2nw
cj3pzm56600023k5keb17x9za	cj41z7aqj00061w5wdepdt2nw
cj441o5it00002z5n3u00e75s	cj44ffnf000002y5o9cjc6qqg
cj44fl5fi00002y5o96zk0wc5	cj44iq0v800013i63l8n1z7oo
cj45jl2wa00003i6393hvbov4	cj45jnyk600093k5n8vlblchw
cj45jrcos000h3k5nstsf1fao	cj45jsu84000i3k5nod8jrrn9
cj45jn2lr00073k5ntkdaulp3	cj45jmhif00043k5nrrgo2enm
cj45k31a600113k5nmugr8y08	cj45k2r3b00103k5nslbc4rdd
cj46pzgh90000265kdbn82vus	cj46mgtyz000d3k5nd2avp63x
cj47c3v0f00083k63bpvdxxe1	cj47c50qu000e3k63ornob9tc
cj47c4k1q000c3k6366ndez2r	cj47c50qu000e3k63ornob9tc
cj47c4efq000b3k63ongr1v3a	cj47c55sd000f3k63nn5dtnv3
cj47c2j7t00033k632swx2y9p	cj47c50qu000e3k63ornob9tc
cj47c3v0f00083k63bpvdxxe1	cj47c61at000g3k633u7h20t6
cj47c3v0f00083k63bpvdxxe1	cj47c6cn0000h3k63ykkx9bwu
cj47c4k1q000c3k6366ndez2r	cj47c6cn0000h3k63ykkx9bwu
cj47c433z00093k63ifq9bz05	cj47c6cn0000h3k63ykkx9bwu
cj47c3v0f00083k63bpvdxxe1	cj47c73ic000i3k63oapfvjsi
cj47c4efq000b3k63ongr1v3a	cj47c76nf000j3k63qxppgdo9
cj47c9ish000s3k63dfykh369	cj47c76nf000j3k63qxppgdo9
cj47c9kdj000t3k63he6i4ktr	cj47c76nf000j3k63qxppgdo9
cj47c433z00093k63ifq9bz05	cj47c9kdj000t3k63he6i4ktr
cj47c47jb000a3k635b406i1z	cj47c9ish000s3k63dfykh369
cj47c9fbb000r3k63q6vid8wh	cj47cc3h8000x3k63u0t5ma7x
cj47ccnub000y3k63ax2658bh	cj47cdc6200113k636jn8o3ve
cj47c47jb000a3k635b406i1z	cj47ccsde000z3k6358empyis
cj47ccxkq00103k63t1gozz89	cj47c2mgx00043k6331i2es0z
cj47c2mgx00043k6331i2es0z	cj47c9ish000s3k63dfykh369
cj47cenzk00123k63y6t51y2c	cj47ccnub000y3k63ax2658bh
cj47ccxkq00103k63t1gozz89	cj47cenzk00123k63y6t51y2c
cj47cenzk00123k63y6t51y2c	cj47ccsde000z3k6358empyis
cj47cgc9h00183k63aiajm17x	cj47c348p00053k63e8kuvszc
cj47c3o1b00073k63o15ewlq5	cj47c4k1q000c3k6366ndez2r
cj47cdc6200113k636jn8o3ve	cj47chbbw00193k63gwomxa5u
cj47cdc6200113k636jn8o3ve	cj47chk7g001b3k63xisccdgi
cj47cidw3001e3k63v2wrm15m	cj47chhgs001a3k63adj2u4qs
cj47ciyrm001f3k63kg5m6unm	cj47chk7g001b3k63xisccdgi
cj47ij5tj00013i63nnt6bc5c	cj47ijjms00023i63639a7mrv
cj47ijjms00023i63639a7mrv	cj47ijn5a00033i631mlon7am
cj47ijn5a00033i631mlon7am	cj47ijpwe00043i63o3cvn96f
cj47ijpwe00043i63o3cvn96f	cj47ijs1500053i63n820dep5
cj47ijuy700063i635qa4ipyq	cj47ijxdq00073i6331h3zdb4
cj47ijxdq00073i6331h3zdb4	cj47ik07100083i63b4xaid1t
cj47ijuy700063i635qa4ipyq	cj47ijs1500053i63n820dep5
cj47inptk000a3i63hdf26uzm	cj47ik07100083i63b4xaid1t
cj47inxc8000b3i63himcjtf2	cj47ik07100083i63b4xaid1t
cj47ipe6j000d3i63v62vjplp	cj47ik07100083i63b4xaid1t
cj47ips5e000g3i63qt4snml4	cj47j09lg00001w5iu6uefyww
cj47j09lg00001w5iu6uefyww	cj47j1njk000h3i63cvvwjx97
cj47j09lg00001w5iu6uefyww	cj47j1kd600011w5if4cvhdr7
cj47j1njk000h3i63cvvwjx97	cj47j1otj000i3i63btbku1db
cj47j1q78000j3i636wgr73fq	cj47j22q5000k3i63o5aioed5
cj48nxtoz00003i5n11fju63i	cj48nxw7y00013i5ngyp664gy
cj48o50eb00002y5oi2f4g8j1	cj48nxw7y00013i5ngyp664gy
cj48nxw7y00013i5ngyp664gy	cj48nxtoz00003i5n11fju63i
cj48o50eb00002y5oi2f4g8j1	cj48oen2i00012y5oahvwm7fw
cj48t0hvw0004315o39ma1io7	cj48t0f270003315o3zihfb5m
cj48t0s9e0005315obantxrlg	cj48t0f270003315o3zihfb5m
cj48tfv4u0002315o78t7dqnl	cj48tgi3300013u69k3bkpmdh
cj48tfcj000003u69kc4b5ij5	cj48tfv4u0002315o78t7dqnl
cj48tgi3300013u69k3bkpmdh	cj48th13a0003315ot679llkd
cj4abu12700022y5ocl9t1v80	cj4abnnax00092y5otq29kicy
cj4abu12700022y5ocl9t1v80	cj4abnd3g00082y5o5up5giyp
cj4abuu2d00032y5o8gf56rw1	cj4abtc6c00012y5o4rrodzqc
cj4abu12700022y5ocl9t1v80	cj4abwsem00042y5o6tcm4qyq
cj4abzgml00062y5oay5fv96n	cj4abx9fm00001w631ix0vpxk
cj4ac702i000d2y5o7p554e8n	cj4bb404r00002m5sejovbnka
cj4bcpuet00072y5ojcdghkg9	cj4bcqdha00082y5ovik6cr7p
cj4bcosc700062y5oua6d9jwg	cj4bcqn0p00002m5sjdnivj4y
cj4bcrloa00012m5s5982m4nq	cj4bcqdha00082y5ovik6cr7p
cj4bcqn0p00002m5sjdnivj4y	cj4bcukch00092y5ojhoatztl
cj4bcuuwd000a2y5o73i16evi	cj4bcwa13000b2y5o5cm2ixus
cj4bcwa13000b2y5o5cm2ixus	cj4bcvipe00022m5s5chb8r6c
cj4bcvipe00022m5s5chb8r6c	cj4bcycxe000d2y5o65xqhgo1
cj4bcycxe000d2y5o65xqhgo1	cj4bcw82w00032m5s56mngtfp
cj4bcpuet00072y5ojcdghkg9	cj4bd01ux000f2y5o7ieh62zy
cj4bd0ovs000h2y5o18kx7tu5	cj4bd1hl7000l2y5oh1a4b6z0
cj4bd26d3000m2y5o5xmtmzs0	cj4bcqn0p00002m5sjdnivj4y
cj4eiex0h00082z5ni9a9wrco	cj4eig81a00092z5n09h9d0y1
cj4eihyml00063i63v3ui547w	cj4eihnad00053i63l7j82r1m
cj4eih2xq00043i63ku92v4x3	cj4eijgd9000a2z5n1tbpu5hj
cj4eihnad00053i63l7j82r1m	cj4eijx5e000b2z5nkh6gsiib
cj4eihnad00053i63l7j82r1m	cj4eiki1v000c2z5nxn7ahzmv
cj4ee7kvo00061w63sp5y5yqh	cj4ee6zol00041w63mgbb2iuo
cj4qst5yi00013c64rt7lrhf3	cj4qstiss00033c64pae8def0
cj4qsu4h900043c644bvtu97g	cj4qsudao00053c64rfswbhq5
cj4qsudao00053c64rfswbhq5	cj4qt8ipk00003c64e7791iqh
cj4ymhh4a0004315onbm9xmwk	cj4ymjm3o0009315oqrc5w7yn
cj51emoyx00013s4rmpshe0lw	cj51enlcs00033s4rpjvme9jv
cj51enupi00043s4r0fbddgin	cj51er3sj00053s4r90uvgql0
cj5fqj3j000013q68oqy0dkui	cj5fqk1x500023q68zb7uqnx4
cj5fqu8g100002z5ra2tvdm8z	cj5fquirz00033i62d7pcxgwb
cj5fqu5nj00023i62ao5invw8	cj5fqu8g100002z5ra2tvdm8z
cj5fquirz00033i62d7pcxgwb	cj5fqv70k00012z5rek9nme1r
cj5fqvwf100043i62ve0x7mt5	cj5fqv70k00012z5rek9nme1r
cj5fqu5nj00023i62ao5invw8	cj5fqvwf100043i62ve0x7mt5
cj5fqv70k00012z5rek9nme1r	cj5fqvwf100043i62ve0x7mt5
cj5fqvwf100043i62ve0x7mt5	cj5fqz8x600003i62ni5a6uff
cj5fqs0ti00013i5401q15yqk	cj5fquirz00033i62d7pcxgwb
cj5fqz8x600003i62ni5a6uff	cj5fqzocs00022z5rfidhsg0k
cj5ln04uy0000315nzf1cn0ic	cj5ln0him0000315oewok5r55
cj5ln0q9p0001315nnd99jjet	cj5ln0us10001315o8rhrljy7
cj5ln0us10001315o8rhrljy7	cj5ln3fm10002315ng00j9aa8
cj5ln3fm10002315ng00j9aa8	cj5ln3ov00003315nsmq93zoc
cj5ln421z0004315n5oi8j2cf	cj5ln4l2g0005315n2w7nb9pm
cj5ln4l2g0005315n2w7nb9pm	cj5ln4mvh0006315ndoyrvbi4
cj5ln4mvh0006315ndoyrvbi4	cj5ln4s200007315niccd3stx
cj5ln4s200007315niccd3stx	cj5ln4vep0008315n23c99kj1
cj5ln4vep0008315n23c99kj1	cj5ln4vy40009315nfah6eju4
cj5ln4vy40009315nfah6eju4	cj5ln4wjj000a315nfn3uwlzv
cj5ln4wjj000a315nfn3uwlzv	cj5ln4xaq000b315n9wlt2h3r
cj5ln4xaq000b315n9wlt2h3r	cj5ln4xwt000c315n18hbnpzc
cj5ln4xwt000c315n18hbnpzc	cj5ln4zgm000d315njnnnf7rs
cj5ln4zgm000d315njnnnf7rs	cj5ln502p000e315nxu368613
cj5ln502p000e315nxu368613	cj5ln50ok000f315nyw8bngxi
cj5ln50ok000f315nyw8bngxi	cj5ln5pck000g315nk8rcbc91
cj5ln4l2g0005315n2w7nb9pm	cj5kigrvr0001315o7fr1lpq1
371	cj44ff0xm00012y5o3hdw1cgs
cj5lpvcyj00012c5hhct4v34f	cj5lpwcvm0000315ogbedui71
cj5lpwtba0001315oknkkj4mn	cj5lpxiqw00022c5huhke1kdc
cj5lpxla40002315oijhq4pln	cj5lpxo5w00032c5h6z172gsa
cj5lpwcvm0000315ogbedui71	cj5lpyzmh0001315o2pzpmlss
cj5lpxla40002315oijhq4pln	cj5lpyzmh0001315o2pzpmlss
cj5lq3iu600052c5hibx3p2hm	cj5lq68nx0007315on3o48io7
cj5lq38jq0006315oh3ihbpb3	cj5lq6qca00062c5h4hkg8etg
cj5lq6yxp00072c5hx7h0z9nv	cj5lq30vi0004315oipx62m6e
cj5lq7hgb00082c5hj9sborqi	cj5lq744g0008315oxfaw6d5p
cj5lq7hgb00082c5hj9sborqi	cj5lq6yxp00072c5hx7h0z9nv
cj41z8497000c1w5wvcff56bi	cj41z81ab00091w5w1fblhgxi
cj3pzm2mn00013k5kje2qc99s	367
337	cj41z876h000f1w5w47y9qois
cj4qssxa000003c64szhnu3zr	82
cj5mrli4v00083c6hksy58iu7	cj5mrjqpj00043c6hkw5b9cul
cj656zgyf0001305os8fet4oo	cj656zwhv0000305ojjzxgn87
cj656zwhv0000305ojjzxgn87	cj6570kvy0001305odt82xky6
cj6570kvy0001305odt82xky6	cj6570rr80002305oi5cvxn9o
cj6xc8yde00033i5oxprp2zbo	cj6xc92tm00043i5om3avvto5
cj6xc9cr500063i5oylqpee6u	cj6xc8yde00033i5oxprp2zbo
cj6xc993t00053i5ooqvj7j79	cj6xc92tm00043i5om3avvto5
cj6xc9e4x00073i5owuf3o74s	cj6xc8yde00033i5oxprp2zbo
cj6xc9e4x00073i5owuf3o74s	cj6xc92tm00043i5om3avvto5
cj6xcau0g00093i5o5vbigv9b	cj6xcan9s00083i5ob2vf6b51
cj6xc92tm00043i5om3avvto5	cj6xcan9s00083i5ob2vf6b51
cj6xc993t00053i5ooqvj7j79	cj6xcan9s00083i5ob2vf6b51
cj6xcfmlu000m3i5ooimy102n	cj6xccupy000f3i5oqshz5evi
cj6xcdsvh000h3i5o53ybqwei	cj6xccz9q000g3i5o9uisr5el
cj6xcdx4t000j3i5o7bq2epov	cj6xcdv3p000i3i5o9e4bdqh4
cj6xcfmlu000m3i5ooimy102n	cj6xce911000k3i5oi8ea1lrq
cj41z81ab00091w5w1fblhgxi	cj41z84wl000d1w5wak4waew8
cj41z876h000f1w5w47y9qois	286
cj44ff0xm00012y5o3hdw1cgs	337
cj77hrwuy00023s663kyvv0bg	cj77ht9q100013x66g1b8ysec
cj77hrwuy00023s663kyvv0bg	cj77htg6700033s66xdcf7h9e
cj77htg6700033s66xdcf7h9e	cj77hwm5v00023x66x810d2bk
cj77hrwuy00023s663kyvv0bg	cj77hw5x000043s66sj8c9mfo
cj3vvw8uu00053i5x3djwsp3s	cj3vvqv1q00013i5x1pv6w796
cj77hxxy700053s66roampaaf	cj77htg6700033s66xdcf7h9e
cj7t2x7p00001305p1j6vkvko	cj7t2xbzy0002305p5n8s1ryg
cj77i1c4i00063s66g5stoga6	cj77htg6700033s66xdcf7h9e
cj77i1c4i00063s66g5stoga6	cj77i5i6300073s66rkjeg0dc
cj40bygmn00003i7tv748iwio	cj3pzm79d00043k5km2l2pwc7
337	cj3pzmheu000b3k5k97eyqexk
cj7hqm32600003i60do32gwik	cj44ff0xm00012y5o3hdw1cgs
cj44fe6dt00002y5ouelvvui1	cj44ff0xm00012y5o3hdw1cgs
cj3umm81800012z5kb0afyub3	cj44fe6dt00002y5ouelvvui1
cj7rcmbr20000855dc1f44ha3	cj7rcpkfg0000305psm4y5ewf
cj7rcr7ik0005855dywyb72wo	cj7rcqhw60004855dy984uejt
cj7rcri9z0007855du1k8ij38	cj7rcr7ik0005855dywyb72wo
cj7rcs4g90000305pszfimtpz	cj7rcrcix0006855do03obgb6
cj7t32jz100042a5lkx8jnqq9	cj7t2xvk80003305phryrt14l
cj4abg97z00012y5oj46r6wdm	cj4abuu2d00032y5o8gf56rw1
cj82uwpm600022y61ol0qmhrm	cj82uzhjj00052y61nw70e6fc
cj82v153f00062y617fwayh9x	cj82uzhjj00052y61nw70e6fc
cj87fjdn50001315pwstp2u3t	cj87fjk380003315p3amzr770
cj87fjdn50001315pwstp2u3t	cj87fjl330004315pmxy9sax4
cj87fjoko0005315p1bi49s4x	cj87fjsp30007315p1t2dgz17
cj87fjoko0005315p1bi49s4x	cj87fjrsf0006315pq50vnksl
cj41z7kp300081w5w4vke15b3	367
cj44fe6dt00002y5ouelvvui1	cj7hqm32600003i60do32gwik
371	367
82	cj44fe6dt00002y5ouelvvui1
cj7q0sedt0002855dndnrxquv	cj7q0ri2y0001855d9yjamf7j
cj7rcqhw60004855dy984uejt	cj7rcs4g90000305pszfimtpz
cj5nu9nn70000315s4hd1rfjc	cj80jywja00003f5pqy4tm3e9
cj87fjdn50001315pwstp2u3t	cj87fjgsn0002315pwx3revru
cj87fjoko0005315p1bi49s4x	cj87fjthd0008315p3t9lnyll
cj7q0sk5p0003855dn0rxkkcf	cj7rcmbr20000855dc1f44ha3
cj7rctd9c000a855dcuvjbu69	cj7rcs4g90000305pszfimtpz
cj7t2x7p00001305p1j6vkvko	cj7t2x7gt00002a5l04samucs
cj7t2x7p00001305p1j6vkvko	cj7t2xcb100012a5lvfduj1rd
cj44ff0xm00012y5o3hdw1cgs	cj44fe6dt00002y5ouelvvui1
cj82uwn3900012y619yvotgcs	cj82uzhjj00052y61nw70e6fc
cj87fk4ob0009315p1ppmck76	cj87fkz6d000f315pyz44rd50
cj87fk4ob0009315p1ppmck76	cj87fkqkg000d315p1q7u255o
cj87fk4ob0009315p1ppmck76	cj87fl4uu000h315p3mi4muhg
cj87fk4ob0009315p1ppmck76	cj87fkg63000b315phrfxq7q7
cj87fk4ob0009315p1ppmck76	cj87fka9h000a315p3zp8sl52
cj87fk4ob0009315p1ppmck76	cj87fl2s2000g315pxmwa21si
cj87fk4ob0009315p1ppmck76	cj87fklil000c315pdk4vxtzm
cj87fk4ob0009315p1ppmck76	cj87fkvfu000e315pe2djpz45
cj87fjk380003315p3amzr770	cj87fkvfu000e315pe2djpz45
cj87fjk380003315p3amzr770	cj87fkz6d000f315pyz44rd50
cj87fjgsn0002315pwx3revru	cj87fklil000c315pdk4vxtzm
cj87fjgsn0002315pwx3revru	cj87fka9h000a315p3zp8sl52
cj87fjgsn0002315pwx3revru	cj87fkg63000b315phrfxq7q7
cj87fjgsn0002315pwx3revru	cj87fkqkg000d315p1q7u255o
cj87fjl330004315pmxy9sax4	cj87fl2s2000g315pxmwa21si
cj87fjl330004315pmxy9sax4	cj87fl4uu000h315p3mi4muhg
cj87fjsp30007315p1t2dgz17	cj87fkvfu000e315pe2djpz45
cj87fjsp30007315p1t2dgz17	cj87fklil000c315pdk4vxtzm
cj87fjrsf0006315pq50vnksl	cj87fl2s2000g315pxmwa21si
cj87fjrsf0006315pq50vnksl	cj87fka9h000a315p3zp8sl52
cj87fjrsf0006315pq50vnksl	cj87fkg63000b315phrfxq7q7
cj87fjrsf0006315pq50vnksl	cj87fl4uu000h315p3mi4muhg
cj87fjrsf0006315pq50vnksl	cj87fkqkg000d315p1q7u255o
cj87fjthd0008315p3t9lnyll	cj87fkz6d000f315pyz44rd50
cj80jywja00003f5pqy4tm3e9	cj8bv5juz00002w67m06jy22m
82	cj4ac5wpk000b2y5otoo9w19r
cj8h7r89q0007315pieou2lvq	cj8h7rfo500072c5uacv19ptf
cj8h7o7fu0001315puvh4mhz0	cj8h7olm200042c5u88ntk4go
cj8h7qg670005315pxvqmo3vi	cj8h7q25y00062c5ud9oxtdzw
cj8h7plfg0003315pgb4pheax	cj8h7oqr400052c5uezvxh2ed
82	2305
cj3um0lox00002z5kkoovo1ca	2287
cj8h82er400002r5ugcav6ccj	cj8h7olm200042c5u88ntk4go
82	cj72eh5f900023260fa9q2eic
cj8vwjva60001315p8fiegqa1	cj8vwjw040002315pe8at0xtf
cj8vwjw040002315pe8at0xtf	cj8vwjx630003315p5u4f3izy
cj8wre70800013d65pjd35gk1	cj8wre9u200023d65f1gi79lo
cj8wre9u200023d65f1gi79lo	cj8wrebyr00033d65dh0oy16j
cj8wrekeo00043d65brwbtjfk	cj8wre70800013d65pjd35gk1
cj8wri6b200063d65bd7t8m2i	cj8wrebyr00033d65dh0oy16j
cj8ximw8i00032r5uycym97hy	cj8xin3vq00002r5uws9ibris
cj8xin3vq00002r5uws9ibris	cj8xin4n500012r5ulqvtfi0d
cj8xin4n500012r5ulqvtfi0d	cj8xin54900022r5uq00ksjb3
cj8xin54900022r5uq00ksjb3	cj8xin64z00032r5uothwc5mo
cj8xin64z00032r5uothwc5mo	cj8xin72w00042r5ufotlakfx
cj8xin72w00042r5ufotlakfx	cj8xingdt00052r5ukfwme1ma
cj8ximtqx00022r5ukb4dxt9l	cj8xin3vq00002r5uws9ibris
cj8xj80yg00052r5utgctaefm	cj8xj8jhz00082r5umizp9x73
cj8xj7o4400012r5ur1osaxkt	cj8xj8jhz00082r5umizp9x73
cj8xj82sl00062r5ujbivr1e6	cj8xj8jhz00082r5umizp9x73
cj8xj7uk700032r5ub91qekmo	cj8xj8jhz00082r5umizp9x73
cj4ac5wpk000b2y5otoo9w19r	cj44iq0v800013i63l8n1z7oo
cja9issv700033j5ursvj84h1	cja9isvk400003j5uc6x0o1hz
cja9isvk400003j5uc6x0o1hz	cja9it7yz00003j5utd1y7mf9
cja9it7yz00003j5utd1y7mf9	cja9iufx500043j5uoe2dl32y
cja9iufx500043j5uoe2dl32y	cja9iuscn00003j5u5iqih7sp
cja9iuscn00003j5u5iqih7sp	cja9iut6j00013j5uspcwfshj
cja9uau5100013u5u098c1rbi	cja9ubxmj00023u5uajhxqr0n
cja9ubxmj00023u5uajhxqr0n	cja9ucpba00033u5ug2xxa9e2
cja9ucpba00033u5ug2xxa9e2	cja9uctvr00043u5uadwajcjo
cja9uctvr00043u5uadwajcjo	cja9udyxw00053u5u01ax8r1s
cja9udyxw00053u5u01ax8r1s	cja9ue7at00063u5u26ldqtkh
cja9ue7at00063u5u26ldqtkh	cja9uegqx00073u5uzkl16bbv
cja9uegqx00073u5uzkl16bbv	cja9ueyqk00083u5ugrx3myeb
cja9ueyqk00083u5ugrx3myeb	cja9uf7y700093u5ub6afyvrg
cja9uf7y700093u5ub6afyvrg	cja9ufeqs000a3u5ut7cqdiga
cja9ufeqs000a3u5ut7cqdiga	cja9uflo8000b3u5urxo3yma8
cja9uflo8000b3u5urxo3yma8	cja9uft4d000c3u5ucre3hbg1
cjac91bly00003h5p0ufh7kcr	cj445uqoj001a3k5n3xq6e4gn
cj4ac5wpk000b2y5otoo9w19r	cjaf35b8s00013b5xui1g4pie
cjaf35b8s00013b5xui1g4pie	cjak1nokt0000315s9lnp1urc
cj80jywja00003f5pqy4tm3e9	cjak1omwk0002315s1f6nhl3c
cjak1ov3z0003315s3kouy2d5	cjak1ofiy0001315su6w0ot22
cjasexn5200022y5q6gfety4b	cjasex1h300033b61tomgyp2f
cjak1ofiy0001315su6w0ot22	cjay1iv7r00002z5p8shuxb80
cjay1iv7r00002z5p8shuxb80	cjay1iwii00012z5pjjk5nx8a
cjay1iwii00012z5pjjk5nx8a	cjay1ixbi00022z5pn1qp3xsv
cjay1ixbi00022z5pn1qp3xsv	cjay1iy5m00032z5p5fb5h65r
cjay1iy5m00032z5p5fb5h65r	cjay1iyy600042z5p1222ywij
\.


--
-- Data for Name: containment; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY containment (parentid, childid) FROM stdin;
293	447
442	846
864	870
911	922
922	912
756	758
831	822
892	926
1129	1371
1032	1049
1133	1416
293	208
293	210
293	211
293	286
1134	1130
293	207
1134	1133
1140	1141
1140	1143
293	273
293	205
1140	1160
1140	1179
293	187
293	268
1140	1201
293	75
1140	1207
1402	1438
1131	1445
1129	1463
1131	1465
1132	1470
1584	1591
1584	1599
1584	1611
1584	1616
1584	1621
1584	1636
1516	1521
1695	1716
402	514
371	369
1695	1774
402	518
371	367
1724	1800
1732	1713
1695	1838
389	524
1724	1847
337	371
293	387
389	390
1695	1876
390	392
389	395
1695	1884
524	526
1761	1882
1887	1794
1887	1841
395	397
389	532
1887	1908
1695	1914
1724	1935
532	537
532	535
389	541
389	543
541	543
1695	1940
389	402
1724	1952
1695	1961
389	546
541	546
1695	1978
1695	1986
524	550
1695	2051
1695	2056
402	555
395	405
389	559
559	557
2112	2149
2112	2151
390	411
389	414
416	417
1750	1925
2200	2206
416	422
416	425
417	425
431	432
431	435
2224	2236
2224	2238
2224	2281
389	566
541	566
2285	2296
559	2298
389	576
541	576
389	579
541	579
389	584
2327	2294
2332	2319
559	587
2365	2366
390	590
293	2383
584	414
559	599
389	603
603	605
603	608
389	611
389	620
414	501
873	874
892	894
821	812
821	768
821	792
831	769
911	919
892	930
873	992
1234	1347
1129	1373
1140	1145
1140	1209
1129	1468
1234	1321
1132	1502
1131	1509
1130	1511
1133	1524
1133	1527
1516	1527
1132	1530
1516	1524
1584	1587
1516	1518
1470	1530
1470	1496
1405	1400
1405	1410
1695	1721
1695	1724
1695	1729
1695	1750
1761	1754
1761	1768
1695	1814
1695	1826
1724	1838
1695	1855
1761	1855
1695	1860
1732	1884
1695	1887
1732	1898
1695	1925
1695	1952
1695	1973
1695	2017
1695	2077
1695	2095
1695	2160
2095	2077
2183	1961
712	1082
2285	2303
2285	2329
2285	2342
2365	2368
293	2385
603	620
620	617
873	876
873	879
387	187
795	803
892	896
892	899
892	901
892	907
892	933
1001	1025
1402	1420
1140	1155
1140	1166
1140	1175
1130	1435
1465	1474
82	cj3x5mqv000002y5opqgqunku
1470	1476
1470	1502
1405	1418
1405	1548
1402	1414
1134	1704
1695	1732
1695	1758
1761	1814
1695	1847
1695	1870
1724	1870
1695	1898
1887	1914
1695	1945
1695	2011
1695	2083
2095	1978
2095	1930
389	2112
2183	2011
2191	1999
2221	2222
1120	2285
2285	2305
2310	2308
2285	2319
2285	2332
2332	2317
2327	2296
2365	2370
820	428
387	207
758	822
758	750
901	899
892	904
911	916
1133	1376
142	1092
389	1114
590	1120
1140	1151
1140	1177
1140	1217
1222	1219
1131	1438
1402	1454
1130	1478
1465	1507
429	442
1584	1595
1584	1607
1584	1626
1584	1631
1376	1481
1695	1706
1698	1706
1695	1743
1724	1738
1695	1752
1695	1754
1695	1756
1724	1750
1695	1768
1724	1716
1695	1818
1695	1844
1732	1844
1713	1794
1738	1774
1695	1930
1695	1935
1724	1945
1724	1978
1695	1999
1695	2029
1724	2069
1724	2083
2095	1935
2149	2151
402	1643
1750	2062
1750	2183
2183	1986
2183	2017
2183	1973
2191	2056
2191	2051
2221	2224
2221	2226
2285	2289
2310	2298
559	2314
2365	2372
390	626
390	628
390	630
559	861
728	734
728	723
75	207
795	805
1132	1400
1131	1402
1129	1483
1032	1033
1032	1041
1032	1043
1140	1171
1140	1196
1140	1203
1140	1219
1222	1217
1130	1554
1120	1573
1584	1585
1584	1603
389	1645
389	1647
1376	1422
1695	1708
1695	1738
1761	1743
1695	1782
1761	1797
1695	1810
1732	1729
1713	1810
1695	1822
1695	1882
1695	2039
1695	2062
1695	2088
1750	2075
2095	2069
2095	2029
2112	2114
2112	611
397	1647
1120	1557
1750	2017
1750	1973
2191	2062
2241	2221
2241	389
2241	416
2289	2287
2285	2317
2332	2296
2332	2342
559	2358
2112	2391
2391	2393
2398	2114
153	2412
153	2416
2418	2420
2418	2423
727	724
723	727
725	723
864	524
911	912
807	809
797	800
389	1366
1130	1381
1130	1404
1032	1035
1032	1045
1402	1445
1131	1452
1134	1131
1134	1129
1134	1132
1140	1183
1140	1194
1130	1493
1133	1516
1130	1534
1129	1537
1114	1557
429	432
389	1649
402	1693
1695	1711
1695	1747
1756	1782
1695	1800
1724	1826
1695	1831
1887	1810
1695	1896
1724	2029
1695	2069
1695	2075
1724	2088
2095	2046
1750	1961
1750	2056
1750	2011
2183	1925
1750	2191
2191	2075
2221	2274
2285	2294
2285	2327
559	2360
2112	2395
559	2401
153	2414
153	2420
389	864
402	885
559	890
75	273
911	914
794	807
794	795
1130	1383
1132	1405
1402	1408
1004	1006
1032	1037
1132	1418
1140	1149
1140	1185
1140	1222
1131	1420
1133	1422
1130	1487
1130	1490
1133	1518
1376	1378
1132	1548
1376	1412
1134	1695
1695	1713
1761	1752
1711	1782
1761	1758
1695	1790
1695	1794
1698	1833
1695	1841
1695	1908
1724	1930
1695	2046
1838	1945
397	1645
397	1649
1847	2160
1750	2051
1750	2095
402	2200
2224	2230
2221	2277
559	2308
559	2310
389	2321
2327	2329
2358	2360
2395	611
153	2418
873	887
922	914
911	986
738	725
911	988
1234	1351
293	1368
1130	1386
1132	1410
794	797
1133	1412
1132	1461
687	686
687	689
1132	1476
687	692
687	694
1132	1496
1465	1504
1133	1521
1376	1416
1470	1461
1376	1457
1695	1761
1695	1797
1695	1833
1713	1818
1761	1896
1698	1708
1698	1711
1724	2046
1750	1999
1750	1986
2095	1940
402	2202
2200	2204
1698	1132
2224	2232
2224	2234
2285	2287
2303	2305
2310	2314
402	2321
402	2363
293	2381
2112	2398
2418	2425
1041	1049
cj3px3hfv00062z6046a7p2nk	cj3pxm57i00002z5zfl68o9mf
1822	cj3pxnl7100003k5k9ypfupiz
1695	cj3pxnl7100003k5k9ypfupiz
821	cj3pzlyg700003k5kpw5yfv46
293	cj3pzlyg700003k5kpw5yfv46
293	cj3pzm2mn00013k5kje2qc99s
293	cj3pzm56600023k5keb17x9za
293	cj3pzm6ut00033k5kln899ae7
293	cj3pzm79d00043k5km2l2pwc7
293	cj3pzm8sl00053k5k24mo5zwx
293	cj3pzm9a500063k5km8k1b1w7
293	cj3pzmarc00073k5k1qg1doml
293	cj3pzmbc700083k5k59aahdsp
293	cj3pzmbrp00093k5k37vdw1z9
293	cj3pzmcdh000a3k5kqy2dxp2w
268	cj3pzmheu000b3k5k97eyqexk
293	cj3pzmheu000b3k5k97eyqexk
cj3pzm79d00043k5km2l2pwc7	371
337	208
208	286
286	210
293	cj3pzoznn000c3k5k2knahhz2
204	2383
2383	2381
2383	cj3pzqb61000d3k5kxdom6ao4
2383	cj3pzqemw000j3k5khswadle9
2383	cj3pzqf42000k3k5kbfyedx0r
cj3pzqda9000g3k5ktvwnwgxf	cj3pzqe6g000i3k5k2wie3u3e
cj3pzqb61000d3k5kxdom6ao4	cj3pzqbo9000e3k5kewgr3hao
cj3pzqckp000f3k5kkk8q5qui	cj3pzqb61000d3k5kxdom6ao4
cj3u58abt0000315laro2hzvl	cj3u591v50001315l6c24rbzv
cj3u58abt0000315laro2hzvl	cj3u595z90002315lprpavwmr
cj3u58abt0000315laro2hzvl	cj3u59q3a0004315lb4f5atkg
cj3stu3sn00013i603e3y4n0d	cj3u80mvu00003w5dqsgfc6q9
cj3u80mvu00003w5dqsgfc6q9	cj3u82fs100033w5dwix8lv0m
cj3uav0nf00003u5cm2zt00sn	cj3uayqsh00015t5c5a50wxw4
cj3ufs32q00012z5ka8up4v5a	cj3uftf9g00022z5kknf0hc3t
cj3ufoxpl00002z5kjgb5f1ie	cj3uftf9g00022z5kknf0hc3t
cj3uftf9g00022z5kknf0hc3t	cj3uftytt00032z5kykhrx4ap
cj3ufoxpl00002z5kjgb5f1ie	cj3uftytt00032z5kykhrx4ap
cj3ufs32q00012z5ka8up4v5a	cj3ufxet200082z5kcxr7u1u0
cj3ufoxpl00002z5kjgb5f1ie	cj3ufxet200082z5kcxr7u1u0
cj3ufoxpl00002z5kjgb5f1ie	cj3ug8lgo00092z5kw7rs356v
cj3ugdnkz000f2z5k6glg8qzk	cj3ug9vq9000c2z5kdglisto7
cj3ufoxpl00002z5kjgb5f1ie	cj3uhn8sy000s2z5kjm76wj8a
cj3uhi48r000q2z5k3r0dzkde	cj3uhn8ot000r2z5ka6q38e26
cj3uhi48r000q2z5k3r0dzkde	cj3uhn8wr000t2z5kowxzx6bh
cj3um0lox00002z5kkoovo1ca	cj3um0xn800012z5kr8obr24i
cj3um0lox00002z5kkoovo1ca	cj3um0ylv00022z5kls9wxn0l
cj3um0lox00002z5kkoovo1ca	cj3um347000012z5kt730m1us
cj3um330s00002z5kb4hv3p5l	cj3um36gt00022z5kbocl79vl
cj3um0lox00002z5kkoovo1ca	cj3um36gt00022z5kbocl79vl
cj3um330s00002z5kb4hv3p5l	cj3um1iqt00032z5k71nn8nvx
cj3um330s00002z5kb4hv3p5l	cj3um0xn800012z5kr8obr24i
cj3um0lox00002z5kkoovo1ca	cj3um1x8a00042z5kuyadylsh
cj3um330s00002z5kb4hv3p5l	cj3um3pts00032z5kcc0fy6ah
cj3um0lox00002z5kkoovo1ca	cj3um3pts00032z5kcc0fy6ah
cj3um1x8a00042z5kuyadylsh	cj3um0ylv00022z5kls9wxn0l
cj3um330s00002z5kb4hv3p5l	cj3um3xlw00002z5kckzzczs0
cj3um0lox00002z5kkoovo1ca	cj3um3xlw00002z5kckzzczs0
cj3um0lox00002z5kkoovo1ca	cj3um5rnk00082z5kvnb80rvm
cj3um4fqb00022z5knz9ieufs	cj3um4wse00032z5klh4b7qtk
cj3um0lox00002z5kkoovo1ca	cj3um5opt00072z5krz63ceih
cj3um0lox00002z5kkoovo1ca	cj3um51fg00042z5kcqtjinj1
cj3um0lox00002z5kkoovo1ca	cj3um46jx00012z5kttwift1z
cj3um0lox00002z5kkoovo1ca	cj3um5nd100062z5kfvafd25t
cj3um4fqb00022z5knz9ieufs	cj3um51fg00042z5kcqtjinj1
cj3um0lox00002z5kkoovo1ca	cj3um5luh00052z5k3g1mjg9y
cj3um0lox00002z5kkoovo1ca	cj3um4fqb00022z5knz9ieufs
cj3um5luh00052z5k3g1mjg9y	cj3um5opt00072z5krz63ceih
cj3um0lox00002z5kkoovo1ca	cj3um4wse00032z5klh4b7qtk
cj3um330s00002z5kb4hv3p5l	cj3um46jx00012z5kttwift1z
cj3um1x8a00042z5kuyadylsh	cj3um65r800092z5kv1skjgcv
cj3um0lox00002z5kkoovo1ca	cj3um65r800092z5kv1skjgcv
cj3um5luh00052z5k3g1mjg9y	cj3um5nd100062z5kfvafd25t
cj3um1x8a00042z5kuyadylsh	cj3ume9om000a2z5kn4akqeo4
cj3um0lox00002z5kkoovo1ca	cj3umfxmw000c2z5kl10uazp7
cj3um0lox00002z5kkoovo1ca	cj3umg46b000d2z5k9f9ken9z
cj3umm81800012z5kb0afyub3	cj3umo52t00022z5ksf0qivvq
cj3umu1hf00002z5k2fx0rhr3	cj3umu4lp00012z5k9dxhv6l7
cj3umvbyg00032z5kkir05kds	cj3umuexg00022z5ki9cstwo9
cj3umu4lp00012z5k9dxhv6l7	cj3umvpnz00042z5kprac1f96
cj3umvpnz00042z5kprac1f96	cj3umuexg00022z5ki9cstwo9
cj3un0smj00062z5k62t4sfet	cj3un0xd300072z5ktbscu6fw
cj3umu4lp00012z5k9dxhv6l7	cj3un0smj00062z5k62t4sfet
cj3vi73kb00002z5ktsa4yy3r	cj3vi78ua00012z5krh5v1uk7
2112	cj3vi78ua00012z5krh5v1uk7
cj3vugeid00002z5zqppzn1de	cj3vuhkhy00012z5zv4llf5cr
cj3vugeid00002z5zqppzn1de	cj3vuhp1v00022z5z0wmlyqb2
cj3vuhkhy00012z5zv4llf5cr	cj3vuii5n00032z5zf8i2awu3
cj3vugeid00002z5zqppzn1de	cj3vuii5n00032z5zf8i2awu3
cj3vuhkhy00012z5zv4llf5cr	cj3vuiok000042z5z77obqoeq
cj3vugeid00002z5zqppzn1de	cj3vuiok000042z5z77obqoeq
1120	cj3vul8p40000315lfa6djk6i
cj3vul8p40000315lfa6djk6i	cj3vvpeax0000315lxxw93kys
cj3vul8p40000315lfa6djk6i	cj3vvpf0d00002z5xmpmj6emo
cj3x3hrmg00003k60qqaazy8a	cj3su087a000a3i60qifunrxj
cj3x3ns2b00003k5krzsuquil	cj3su3phl000e3i6035mr5l6h
cj3vvriki00012z5xrg9waq5j	cj3vvpeax0000315lxxw93kys
cj3vvriki00012z5xrg9waq5j	cj3vvv7kn00032z5x8kp51tix
cj3vvpeax0000315lxxw93kys	cj3vvv7kn00032z5x8kp51tix
cj3x3ns2b00003k5krzsuquil	cj3su0v9h000c3i60ajxotps5
cj3x3ns2b00003k5krzsuquil	cj3uasvtf00005t5ceor3ujy9
cj3x3ns2b00003k5krzsuquil	cj3su087a000a3i60qifunrxj
cj3x3ns2b00003k5krzsuquil	cj3stwxsj00053i60her1775v
cj3x4n3ry00043k5kxem8f6k9	cj3x4tq4o00053k5k6alnuxos
2224	cj3x5tb2200012y5ov5ncedwt
cj3ya6mfg0009315ozjq4e2gf	cj3yai9gx0000315owln7e9b1
cj3ya6mxd00092z63ublkrpst	cj3yakfhz000b2z638525eekm
82	cj3yaln6j00012d5mtmsfeka2
82	cj3yb1djc00002z63en1rgwk7
cj3ybberl00002z63a62bstta	cj3ybcmsg00022z63d6q4aml4
cj3y9ooxp0000315o2urjuxc3	cj3ybcmsg00022z63d6q4aml4
cj3ybcmsg00022z63d6q4aml4	cj3ybcza600032z63e3yoh309
cj3ybchm700012z63rsd494sg	cj3ybdg1i00042z633t79imgv
cj3ybdg1i00042z633t79imgv	cj3ybdzfs00062z63c0c6g0og
cj3y9ooxp0000315o2urjuxc3	cj3ybdzfs00062z63c0c6g0og
82	cj3z2uuvx00002y5oeq96lva6
82	cj3z2w33r00012y5oi4luoeyn
293	cj41zl56y00012y5o4ms1txo3
2383	cj3pzqbo9000e3k5kewgr3hao
2383	cj3pzqckp000f3k5kkk8q5qui
2383	cj3pzqda9000g3k5ktvwnwgxf
2383	cj3pzqdrc000h3k5kb3aawjoz
2383	cj3pzqe6g000i3k5k2wie3u3e
2383	cj3pzqgd4000l3k5ka62uqaz3
cj3pzqckp000f3k5kkk8q5qui	cj3pzqbo9000e3k5kewgr3hao
cj3pzqbo9000e3k5kewgr3hao	cj3pzqda9000g3k5ktvwnwgxf
2383	cj3pzqyvs000m3k5kzcfj1nkz
cj3pzqyvs000m3k5kzcfj1nkz	cj3pzr24o000n3k5k8c5a9smv
2383	cj3pzr24o000n3k5k8c5a9smv
2383	cj3pzr4pc000o3k5knla9026z
cj3u595z90002315lprpavwmr	cj3u59eh30003315lqk5u3lyj
cj3u595z90002315lprpavwmr	cj3u59q3a0004315lb4f5atkg
cj3u595z90002315lprpavwmr	cj3u5ayfy0005315lmupdjiw4
cj3u58abt0000315laro2hzvl	cj3u5ayfy0005315lmupdjiw4
cj3u595z90002315lprpavwmr	cj3u5azbx0006315lr07id9ln
cj3u58abt0000315laro2hzvl	cj3u5azbx0006315lr07id9ln
cj3u5l9z20007315lh9t2m1s4	cj3u5ayfy0005315lmupdjiw4
cj3x3hrmg00003k60qqaazy8a	cj3su4et4000f3i60e3wu4x9w
cj3x3hrmg00003k60qqaazy8a	cj3styv1d00083i60iz4375ka
cj3x3hrmg00003k60qqaazy8a	cj3su3phl000e3i6035mr5l6h
cj3x3hrmg00003k60qqaazy8a	cj3uat9rs00003w5c7zhyzwjp
cj3x3hrmg00003k60qqaazy8a	cj3uasvtf00005t5ceor3ujy9
cj3x3hrmg00003k60qqaazy8a	cj3stwxsj00053i60her1775v
cj3x3ns2b00003k5krzsuquil	cj3su0jye000b3i607noyybc7
cj3x3hrmg00003k60qqaazy8a	cj3x3pi4900013k5k9gjeuaiy
cj3x3hrmg00003k60qqaazy8a	cj3u81m7100023w5dxfev62db
cj3x4tq4o00053k5k6alnuxos	cj3x4tzq700063k5kwz11thn6
cj3su087a000a3i60qifunrxj	cj3u811mw00013w5dv6gddcl8
cj3x4n3ry00043k5kxem8f6k9	cj3x4tzq700063k5kwz11thn6
cj3x81cl200012y5omnoiwqek	cj3x81lhp00022y5ovzapioqf
cj3y9lbho0000315obc5atwzl	cj3y9qub800002z63bxi79pm2
cj3y9ooxp0000315o2urjuxc3	cj3y9rx1500012z63jv6actr6
cj3y9lbho0000315obc5atwzl	cj3y9rx1500012z63jv6actr6
cj3y9ooxp0000315o2urjuxc3	cj3y9s2qh00022z63r1tvww4d
cj3y9lbho0000315obc5atwzl	cj3y9s2qh00022z63r1tvww4d
cj3y9s2qh00022z63r1tvww4d	cj3y9sn6100032z63irxo9wrw
cj3y9lbho0000315obc5atwzl	cj3y9sn6100032z63irxo9wrw
cj3y9rx1500012z63jv6actr6	cj3y9sw0b00042z63ptf0s41k
cj3y9lbho0000315obc5atwzl	cj3y9sw0b00042z63ptf0s41k
cj3y9rx1500012z63jv6actr6	cj3y9t79t00052z63wx43hmds
cj3y9lbho0000315obc5atwzl	cj3y9t79t00052z63wx43hmds
cj3y9lbho0000315obc5atwzl	cj3y9tcba00062z63reun8i8b
cj3y9p9u60001315o8hwy4ik4	cj3y9vbjz0002315ozvun1jz0
cj3y9lbho0000315obc5atwzl	cj3y9vbjz0002315ozvun1jz0
cj3y9lbho0000315obc5atwzl	cj3y9wii90007315of230mjym
cj3y9ooxp0000315o2urjuxc3	cj3ya6mfg0009315ozjq4e2gf
cj3y9ooxp0000315o2urjuxc3	cj3yacf4z0000315o2npoqf8h
82	cj3yakrq900002d5mye06dlwb
cj3u80mvu00003w5dqsgfc6q9	cj3uaxqsl00003u5a2kos0h8x
cj3uav0nf00003u5cm2zt00sn	cj3uaytm500025t5c3p753gcj
cj3uftf9g00022z5kknf0hc3t	cj3ufu5vn00042z5k3cghntas
cj3ufoxpl00002z5kjgb5f1ie	cj3ufu5vn00042z5k3cghntas
cj3uftf9g00022z5kknf0hc3t	cj3ufub6300052z5kbhy9r327
cj3ufoxpl00002z5kjgb5f1ie	cj3ufub6300052z5kbhy9r327
cj3uftf9g00022z5kknf0hc3t	cj3ufugkq00062z5kjqh0a737
cj3ufoxpl00002z5kjgb5f1ie	cj3ufugkq00062z5kjqh0a737
cj3ufoxpl00002z5kjgb5f1ie	cj3ufv0r000072z5kluu0xzvz
cj3ufs32q00012z5ka8up4v5a	cj3ufv0r000072z5kluu0xzvz
82	cj3yasiso00032d5mx3g4ifw7
cj3ufoxpl00002z5kjgb5f1ie	cj3ug91l3000a2z5klm7zzirz
cj3ufoxpl00002z5kjgb5f1ie	cj3ug9g9u000b2z5kleq0bmhy
cj3ufoxpl00002z5kjgb5f1ie	cj3ug9vq9000c2z5kdglisto7
cj3ufoxpl00002z5kjgb5f1ie	cj3ug9zm2000d2z5kpia2c70x
cj3ufoxpl00002z5kjgb5f1ie	cj3ugdizm000e2z5kczg5j8ct
cj3ufoxpl00002z5kjgb5f1ie	cj3ugdnkz000f2z5k6glg8qzk
cj3ufoxpl00002z5kjgb5f1ie	cj3ugdq09000g2z5ktnzev7jf
cj3ufoxpl00002z5kjgb5f1ie	cj3ugdq9m000h2z5krlb1pceq
cj3ufoxpl00002z5kjgb5f1ie	cj3ugdqkn000i2z5klqhs6cx3
cj3ufoxpl00002z5kjgb5f1ie	cj3uge7b8000k2z5k5id7h1ui
cj3ug9g9u000b2z5kleq0bmhy	cj3ugdnkz000f2z5k6glg8qzk
cj3ugdnkz000f2z5k6glg8qzk	cj3ug9zm2000d2z5kpia2c70x
cj3ufoxpl00002z5kjgb5f1ie	cj3ugza0y000m2z5kgl2ugbef
cj3ufoxpl00002z5kjgb5f1ie	cj3ugzaat000o2z5kcgj05psj
cj3ufoxpl00002z5kjgb5f1ie	cj3uh1ky7000p2z5krbssybae
cj3ufoxpl00002z5kjgb5f1ie	cj3uhi48r000q2z5k3r0dzkde
cj3umm81800012z5kb0afyub3	2357
820	429
cj3ybchm700012z63rsd494sg	cj3ybevem00012z63zu5266io
cj3umu4lp00012z5k9dxhv6l7	cj3umvbyg00032z5kkir05kds
cj3umvpnz00042z5kprac1f96	cj3umxd0p00052z5khl0jr3aq
2112	cj3vi73kb00002z5ktsa4yy3r
cj3y9ooxp0000315o2urjuxc3	cj3ybevem00012z63zu5266io
cj3y9ooxp0000315o2urjuxc3	cj3ybf70j00022z63bs5je30r
cj3y9ooxp0000315o2urjuxc3	cj3ycqs6q0003315o0shc3va8
cj3ycszf00005315ov1d6kvdu	cj3yct9ef0006315o1waj1vqe
cj3y9ooxp0000315o2urjuxc3	cj3yct9ef0006315o1waj1vqe
cj3y9ooxp0000315o2urjuxc3	cj3y9vpyt0003315oe0tn5y6n
cj3y9ooxp0000315o2urjuxc3	cj3y9vu2z0004315og00g6y0e
cj3y9ooxp0000315o2urjuxc3	cj3y9xvbx0008315onn0jdyia
cj3y9ooxp0000315o2urjuxc3	cj3y9vzut0005315ocykl4l8j
cj3y9ooxp0000315o2urjuxc3	cj3y9w4da0006315owv19fzns
cj3ydanh900032d5m33k0cc6j	cj3y9vpyt0003315oe0tn5y6n
cj3ydanh900032d5m33k0cc6j	cj3y9w4da0006315owv19fzns
cj3pzr24o000n3k5k8c5a9smv	cj3pzr4pc000o3k5knla9026z
cj3pzr24o000n3k5k8c5a9smv	cj3pzqyvs000m3k5kzcfj1nkz
204	cj3qkmxk100001w5ymw0cxjt7
204	cj3qkn1p300011w5ypbdmg6mn
cj3u58abt0000315laro2hzvl	cj3u5l9z20007315lh9t2m1s4
82	153
cj3stt8lt00003i60pf5j1qlk	cj3stu3sn00013i603e3y4n0d
cj3stu3sn00013i603e3y4n0d	cj3stulfr00023i60naiq4oba
cj3stu3sn00013i603e3y4n0d	cj3stvc9y00033i60eu00ymy1
cj3stu3sn00013i603e3y4n0d	cj3stvwph00043i60vbv4p8pr
cj3stx6oj00063i60o1a4ofjj	cj3stwxsj00053i60her1775v
cj3stx6oj00063i60o1a4ofjj	cj3sty9gh00073i60cr24n6sr
cj3stx6oj00063i60o1a4ofjj	cj3styv1d00083i60iz4375ka
cj3stu3sn00013i603e3y4n0d	cj3stzs4v00093i60nmjofb7t
cj3stx6oj00063i60o1a4ofjj	cj3su087a000a3i60qifunrxj
cj3stx6oj00063i60o1a4ofjj	cj3su0jye000b3i607noyybc7
cj3stx6oj00063i60o1a4ofjj	cj3su0v9h000c3i60ajxotps5
cj3stx6oj00063i60o1a4ofjj	cj3su164l000d3i60dt0srq97
cj3stx6oj00063i60o1a4ofjj	cj3su3phl000e3i6035mr5l6h
cj3stx6oj00063i60o1a4ofjj	cj3su4et4000f3i60e3wu4x9w
cj3stu3sn00013i603e3y4n0d	cj3su7798000g3i6043yab96g
712	1092
389	cj3tydvyn00002z5kp6rgvsti
cj3tydvyn00002z5kp6rgvsti	cj3tye0yp00012z5kcyk550n2
389	cj3tye0yp00012z5kcyk550n2
cj3u3bmyw00003k5krhjpu9gp	cj3u3dlmo00013k5kpv3a4ods
cj3u3bmyw00003k5krhjpu9gp	cj3u3dubc00023k5kd697eedt
cj3u3bmyw00003k5krhjpu9gp	cj3u3e0m000033k5k2k45w9mz
cj3u3bmyw00003k5krhjpu9gp	cj3u3eh1j00043k5kd7potwi4
cj3u3bmyw00003k5krhjpu9gp	cj3u3erx800053k5khoxlm5xy
cj3u3bmyw00003k5krhjpu9gp	cj3u3g2t400063k5k842tnha3
cj3u3g2t400063k5k842tnha3	cj3u3ghoe00073k5k9o3nvir4
cj3u3bmyw00003k5krhjpu9gp	cj3u3ghoe00073k5k9o3nvir4
cj3u3dlmo00013k5kpv3a4ods	cj3u3gnxz00083k5koj4pickz
cj3u3bmyw00003k5krhjpu9gp	cj3u3gnxz00083k5koj4pickz
cj3u3g2t400063k5k842tnha3	cj3u3h2un00093k5k0uhyaywd
cj3u3bmyw00003k5krhjpu9gp	cj3u3h2un00093k5k0uhyaywd
cj3u3g2t400063k5k842tnha3	cj3u3hpfi000a3k5k8cfa5usv
cj3u3bmyw00003k5krhjpu9gp	cj3u3hpfi000a3k5k8cfa5usv
cj3u3g2t400063k5k842tnha3	cj3u3jbxx000b3k5kcfl8g6u7
cj3u3bmyw00003k5krhjpu9gp	cj3u3jbxx000b3k5kcfl8g6u7
cj3u3bmyw00003k5krhjpu9gp	cj3u3k5fq000c3k5kuerpvpkr
cj3u3k5fq000c3k5kuerpvpkr	cj3u3kbf1000d3k5k10jef8v8
cj3u3bmyw00003k5krhjpu9gp	cj3u3kbf1000d3k5k10jef8v8
cj3u3k5fq000c3k5kuerpvpkr	cj3u3kd78000e3k5kz4q2h42v
cj3u3bmyw00003k5krhjpu9gp	cj3u3kd78000e3k5kz4q2h42v
cj3u3kbf1000d3k5k10jef8v8	cj3u3lq78000f3k5k3fk5itvn
cj3u3bmyw00003k5krhjpu9gp	cj3u3lq78000f3k5k3fk5itvn
cj3u3jbxx000b3k5kcfl8g6u7	cj3u3m3fx000g3k5kb9sqfnwf
cj3u3bmyw00003k5krhjpu9gp	cj3u3m3fx000g3k5kb9sqfnwf
cj3u3jbxx000b3k5kcfl8g6u7	cj3u3m4t8000h3k5km7eh1cki
cj3u3bmyw00003k5krhjpu9gp	cj3u3m4t8000h3k5km7eh1cki
cj3u3jbxx000b3k5kcfl8g6u7	cj3u3mci5000i3k5k3i0uziiy
cj3u3bmyw00003k5krhjpu9gp	cj3u3mci5000i3k5k3i0uziiy
cj3u3g2t400063k5k842tnha3	cj3u3n6fv000j3k5kni5pss6b
cj3u3bmyw00003k5krhjpu9gp	cj3u3n6fv000j3k5kni5pss6b
cj3u3g2t400063k5k842tnha3	cj3u3news000k3k5krbx24qi5
cj3u3bmyw00003k5krhjpu9gp	cj3u3news000k3k5krbx24qi5
cj3u3k5fq000c3k5kuerpvpkr	cj3u3oa4b000l3k5k67d4otph
cj3u3bmyw00003k5krhjpu9gp	cj3u3oa4b000l3k5k67d4otph
cj3u3k5fq000c3k5kuerpvpkr	cj3u3ogzv000m3k5kzuuhql9p
cj3u3bmyw00003k5krhjpu9gp	cj3u3ogzv000m3k5kzuuhql9p
cj3u3k5fq000c3k5kuerpvpkr	cj3u3ot2a000n3k5k8kc33cp6
cj3u3bmyw00003k5krhjpu9gp	cj3u3ot2a000n3k5k8kc33cp6
cj3u3dlmo00013k5kpv3a4ods	cj3u3paju000o3k5km2ic9cfh
cj3u3bmyw00003k5krhjpu9gp	cj3u3paju000o3k5km2ic9cfh
cj3u3paju000o3k5km2ic9cfh	cj3u3erx800053k5khoxlm5xy
cj3u3paju000o3k5km2ic9cfh	cj3u3eh1j00043k5kd7potwi4
cj3u3paju000o3k5km2ic9cfh	cj3u3dubc00023k5kd697eedt
cj3u3paju000o3k5km2ic9cfh	cj3u3e0m000033k5k2k45w9mz
cj3u3paju000o3k5km2ic9cfh	cj3u3su09000p3k5kizmbof8o
cj3u3bmyw00003k5krhjpu9gp	cj3u3su09000p3k5kizmbof8o
cj3ufoxpl00002z5kjgb5f1ie	cj3ufs32q00012z5ka8up4v5a
cj3ufoxpl00002z5kjgb5f1ie	cj3ugdqq4000j2z5kgaemrdl1
cj3ug9g9u000b2z5kleq0bmhy	cj3ugdq09000g2z5ktnzev7jf
cj3ugdnkz000f2z5k6glg8qzk	cj3ugdizm000e2z5kczg5j8ct
cj3ufoxpl00002z5kjgb5f1ie	cj3ugz9vw000l2z5ku971y273
cj3ufoxpl00002z5kjgb5f1ie	cj3ugza5u000n2z5kb77c4mve
cj3ugdqkn000i2z5klqhs6cx3	cj3ugdizm000e2z5kczg5j8ct
cj3uh1ky7000p2z5krbssybae	cj3ugdqkn000i2z5klqhs6cx3
cj3ufoxpl00002z5kjgb5f1ie	cj3uhn8ot000r2z5ka6q38e26
cj3ufoxpl00002z5kjgb5f1ie	cj3uhn8wr000t2z5kowxzx6bh
cj3uhi48r000q2z5k3r0dzkde	cj3uhn8sy000s2z5kjm76wj8a
cj3uhi48r000q2z5k3r0dzkde	cj3uho443000u2z5krzazottu
cj3uhi48r000q2z5k3r0dzkde	cj3uho48v000v2z5k3s0wj9dz
cj3uhi48r000q2z5k3r0dzkde	cj3uho4cs000w2z5kix4tg6fd
cj3ufoxpl00002z5kjgb5f1ie	cj3uhosu9000x2z5kv744x5l2
cj3ufoxpl00002z5kjgb5f1ie	cj3uhow2h000y2z5k9tox6jzp
cj3um0lox00002z5kkoovo1ca	cj3um330s00002z5kb4hv3p5l
cj3um330s00002z5kb4hv3p5l	cj3um347000012z5kt730m1us
cj3um0lox00002z5kkoovo1ca	cj3um1iqt00032z5k71nn8nvx
cj3um1x8a00042z5kuyadylsh	cj3umgk3j000e2z5kv5hgbkap
cj3um0lox00002z5kkoovo1ca	cj3umgk3j000e2z5kv5hgbkap
cj3um5luh00052z5k3g1mjg9y	cj3umelfc000b2z5kwomncj7c
cj3um5luh00052z5k3g1mjg9y	cj3umg46b000d2z5k9f9ken9z
cj3um0lox00002z5kkoovo1ca	cj3umelfc000b2z5kwomncj7c
cj3um330s00002z5kb4hv3p5l	cj3umfxmw000c2z5kl10uazp7
cj3um0lox00002z5kkoovo1ca	cj3ume9om000a2z5kn4akqeo4
cj3x3hrmg00003k60qqaazy8a	cj3su164l000d3i60dt0srq97
cj3x3hrmg00003k60qqaazy8a	cj3sty9gh00073i60cr24n6sr
cj3x3hrmg00003k60qqaazy8a	cj3x3ns2b00003k5krzsuquil
cj3x3ns2b00003k5krzsuquil	cj3styv1d00083i60iz4375ka
cj3x3pi4900013k5k9gjeuaiy	cj3x3pl0800023k5ka9kypfja
cj3x3hrmg00003k60qqaazy8a	cj3x3pl0800023k5ka9kypfja
cj3x4tq4o00053k5k6alnuxos	cj3x4uzgu00073k5kya5eh586
cj3x4n3ry00043k5kxem8f6k9	cj3x4uzgu00073k5kya5eh586
cj3x4tq4o00053k5k6alnuxos	cj3x4xj8j00013k5np9qztf3h
cj3y9rx1500012z63jv6actr6	cj3y9thds00072z633owpf7nw
82	cj3ya8iep000a2z639foa1ym9
cj3ya6mfg0009315ozjq4e2gf	cj3yal6ad000c2z63zedjkeaj
cj3y9lbho0000315obc5atwzl	cj3yal6ad000c2z63zedjkeaj
cj3ya6mfg0009315ozjq4e2gf	cj3yalbyv000d2z63igk0wljm
cj3y9lbho0000315obc5atwzl	cj3yalbyv000d2z63igk0wljm
cj3ybberl00002z63a62bstta	cj3ybchm700012z63rsd494sg
cj3y9ooxp0000315o2urjuxc3	cj3ybchm700012z63rsd494sg
cj3ybdg1i00042z633t79imgv	cj3ybdlro00052z631bc9o5u0
cj3y9ooxp0000315o2urjuxc3	cj3ybdlro00052z631bc9o5u0
cj3ybcmsg00022z63d6q4aml4	cj3ybek2e00002z63l8xz0ysv
82	cj3ybjhoq00012d5mk33vq5ii
82	cj3ybr12c00022d5mt4g79cpe
cj3y9lbho0000315obc5atwzl	cj3y9vpyt0003315oe0tn5y6n
cj3y9lbho0000315obc5atwzl	cj3y9vu2z0004315og00g6y0e
cj3um1x8a00042z5kuyadylsh	cj3vylsko00002z5z0kqa3alr
cj3um0lox00002z5kkoovo1ca	cj3vylsko00002z5z0kqa3alr
cj3um5luh00052z5k3g1mjg9y	cj3vymigc00012z5zoo488hjz
cj3um0lox00002z5kkoovo1ca	cj3vymigc00012z5zoo488hjz
204	cj3wrwrh500003e4y8lqy51tb
2383	cj3ws75bu00002y5lk8jpta0u
cj3y9lbho0000315obc5atwzl	cj3y9xvbx0008315onn0jdyia
cj3y9lbho0000315obc5atwzl	cj3y9vzut0005315ocykl4l8j
cj3ycqs6q0003315o0shc3va8	cj3ycrcbf0004315o44un4a20
cj3y9ooxp0000315o2urjuxc3	cj3ydanh900032d5m33k0cc6j
cj3ydanh900032d5m33k0cc6j	cj3y9xvbx0008315onn0jdyia
cj3ya6mxd00092z63ublkrpst	cj3yddv7q00042z63qpcykbx3
cj3ya6mxd00092z63ublkrpst	cj3ydfq4h00052z637agduxgr
cj3ya6mfg0009315ozjq4e2gf	cj3ye4vql0001315orhzrefo0
cj3ya6mfg0009315ozjq4e2gf	cj3ye84tw0002315oc98yaidq
cj3yd26sa0007315ofrkz826z	cj3y9ooxp0000315o2urjuxc3
1134	1700
cj3x384mo00003k5kqd2u1q2c	cj3x38civ00013k5ka7w7y69l
cj3x384mo00003k5kqd2u1q2c	cj3x38ief00023k5khquib42r
cj3ybdg1i00042z633t79imgv	cj3yjo2qi0002315od9o38swp
cj3ybchm700012z63rsd494sg	cj3ykgogm0000315orgivkj9k
cj3ybchm700012z63rsd494sg	cj3ykh4g40000315o71fzvrac
cj3ybdg1i00042z633t79imgv	cj3ykygne0000315o8olggsl3
cj3ybevem00012z63zu5266io	cj3ykypss0001315ozrw45utf
cj3ybdg1i00042z633t79imgv	cj3ykyzke0002315oxxufn80x
cj3ybdg1i00042z633t79imgv	cj3yl0jwa0003315o2kmz04a4
cj3ybdg1i00042z633t79imgv	cj3yl0swu00012z634bm7xq5e
cj3x4tq4o00053k5k6alnuxos	cj3yldjo400003f56c28j276l
cj3ybf70j00022z63bs5je30r	cj3ybfbgw00032z634h39zk8g
cj3ybfbgw00032z634h39zk8g	cj3ybevem00012z63zu5266io
cj3ybchm700012z63rsd494sg	cj3yoi31i0006315oiavnc2c0
cj3yoi31i0006315oiavnc2c0	cj3yoicsf0007315o9sl0vb7c
cj3yoi31i0006315oiavnc2c0	cj3yoifv60008315ocyin1glw
cj3yoi31i0006315oiavnc2c0	cj3yoij0b0009315opzcm7yig
cj3yoi31i0006315oiavnc2c0	cj3yoiym8000a315ord9pt207
cj3yoi31i0006315oiavnc2c0	cj3yoiyp900002b5mnr97ka18
cj3yoiym8000a315ord9pt207	cj3yoij0b0009315opzcm7yig
cj3yoi31i0006315oiavnc2c0	cj3yojg42000b315oroqzwrjp
cj3yoi31i0006315oiavnc2c0	cj3yokj1900012b5mtumnex5k
cj3yoi31i0006315oiavnc2c0	cj3yokolq000e315ohl6uh0rr
cj3yoi31i0006315oiavnc2c0	cj3yol1n800022b5mef1s33jc
cj3yooieh0000315o5ymc17d2	cj3yop6fa0001315o5avvuwew
cj3yooieh0000315o5ymc17d2	cj3you64z0004315o72udoxut
cj3you64z0004315o72udoxut	cj3youmik0006315oxeo92spt
cj3youfci0005315owjqlj8o4	cj3youxlg0007315o4ygsgs1w
cj3you64z0004315o72udoxut	cj3yov4bf00002b5mhrhai798
cj3you64z0004315o72udoxut	cj3yovcfs00012b5md954j8vc
cj3yooieh0000315o5ymc17d2	cj3yow8b00008315owqxdjqun
cj3yooieh0000315o5ymc17d2	cj3yowamx0009315o3brhe0vn
cj3yow8b00008315owqxdjqun	cj3yowszj000b315ods4mnj8l
cj3yow8b00008315owqxdjqun	cj3yoxnbb00032b5m6xn80z12
cj3yowji5000a315ofme37z47	cj3yoxkdp00022b5mi9cdiws3
cj3yow0l200032z631ialegtb	cj3yoqz4p00032z63hpz5mc4m
cj3yoyr50000e315oxdelpoqy	cj3yoyx18000f315ouv2mhyuh
cj3yoldvm00012z63hxrqt2yf	cj3yp1raw00022z638zvcjl7b
cj3yoq2wi00012z6313klqyju	cj3yoz9zo00002z63gpnsd9wv
cj3yp7q3q000g315ouc4b01r6	cj3yp7vmw000h315ovcw5da4z
cj3yp7q3q000g315ouc4b01r6	cj3yp854k000i315ocuypbzif
cj3yp7q3q000g315ouc4b01r6	cj3yp8g4n000j315o7c6v9afw
cj3yolsjb00022z63rqm18xfx	cj3yp9s8200042z63hddqhl4g
cj3yoq2wi00012z6313klqyju	cj3yp9zc500052z63cwzk52qe
cj3yoq2wi00012z6313klqyju	cj3ypa13600062z638ve8dffv
cj3yoq2wi00012z6313klqyju	cj3ypbbnx00082z63sbjltsby
cj3yozfn200012z63seew176g	cj3ypblna00092z63hez6ge89
cj3yooieh0000315o5ymc17d2	cj3ypd99y000l315ohkmnw8pq
cj3yooieh0000315o5ymc17d2	cj3ypdjgp000m315ozyqyty8p
cj3ypdjgp000m315ozyqyty8p	cj3ypdo95000n315onwovsrad
cj3ypdzl8000p315o63tu2rjh	cj3ype2wk000q315ouwxnli4v
cj3ypdzl8000p315o63tu2rjh	cj3ype5sf000r315oi6jplfjd
cj3ypdjgp000m315ozyqyty8p	cj3ypek8f00052b5ml43detqk
cj3ypek8f00052b5ml43detqk	cj3ypetn600072b5mzmimh6yu
cj3ypek8f00052b5ml43detqk	cj3ypexv100082b5m17t5qwq7
cj3x3hrmg00003k60qqaazy8a	cj3su0v9h000c3i60ajxotps5
cj3x3hrmg00003k60qqaazy8a	cj3su0jye000b3i607noyybc7
cj3x3ns2b00003k5krzsuquil	cj3su4et4000f3i60e3wu4x9w
cj3x3ns2b00003k5krzsuquil	cj3sty9gh00073i60cr24n6sr
cj3x3ns2b00003k5krzsuquil	cj3su164l000d3i60dt0srq97
cj3x3pi4900013k5k9gjeuaiy	cj3x3pmjo00033k5kupwbvj3q
cj3x3hrmg00003k60qqaazy8a	cj3x3pmjo00033k5kupwbvj3q
cj3x4n3ry00043k5kxem8f6k9	cj3x4wq6600003k5nbi6mfr1d
cj3y9lbho0000315obc5atwzl	cj3y9p9u60001315o8hwy4ik4
cj3y9lbho0000315obc5atwzl	cj3y9thds00072z633owpf7nw
cj3y9lbho0000315obc5atwzl	cj3y9tnle00082z63ddzhqaxx
cj3vvpeax0000315lxxw93kys	cj3vvuw9i0000265e5rxc30e1
cj3vvpeax0000315lxxw93kys	cj3vvriki00012z5xrg9waq5j
cj3vul8p40000315lfa6djk6i	cj3vvriki00012z5xrg9waq5j
cj3y9rx1500012z63jv6actr6	cj3y9tcba00062z63reun8i8b
cj3vvpeax0000315lxxw93kys	cj3vvvtjw00042z5x2wcbnjf8
cj3y9rx1500012z63jv6actr6	cj3y9tnle00082z63ddzhqaxx
cj3y9ooxp0000315o2urjuxc3	cj3ya6mxd00092z63ublkrpst
cj3ya6mfg0009315ozjq4e2gf	cj3ya7561000a315ohp0dibzt
cj3ya6mxd00092z63ublkrpst	cj3yasg040002315o3uo4t16p
cj3y9ooxp0000315o2urjuxc3	cj3ybberl00002z63a62bstta
cj3y9ooxp0000315o2urjuxc3	cj3ybfbgw00032z634h39zk8g
cj3y9lbho0000315obc5atwzl	cj3y9w4da0006315owv19fzns
cj3y9ooxp0000315o2urjuxc3	cj3ycszf00005315ov1d6kvdu
cj3ya6mxd00092z63ublkrpst	cj3yaewkz0001315ol5p3derv
cj3ydanh900032d5m33k0cc6j	cj3y9vu2z0004315og00g6y0e
cj3ydanh900032d5m33k0cc6j	cj3y9vzut0005315ocykl4l8j
cj3y9s2qh00022z63r1tvww4d	cj3ybberl00002z63a62bstta
cj3ya6mfg0009315ozjq4e2gf	cj3ye4gcs0000315ouqy8i3p9
cj3ya6mxd00092z63ublkrpst	cj3y9lbho0000315obc5atwzl
cj3ybdg1i00042z633t79imgv	cj3yjl9ih0000315okm62gk1v
cj3ybberl00002z63a62bstta	cj3yjl9ih0000315okm62gk1v
cj3ybberl00002z63a62bstta	cj3yjlioh00002z63si2p5gj2
cj3yjlioh00002z63si2p5gj2	cj3yjlp040001315otrho96ex
cj3ybberl00002z63a62bstta	cj3yjlp040001315otrho96ex
cj3ybdg1i00042z633t79imgv	cj3yjoenn00012z63k96z76yj
cj3vvqv1q00013i5x1pv6w796	cj3vvr8x900023i5x9jcfd9zr
cj3vvprwg00003i5x4lpggym4	cj3sty9gh00073i60cr24n6sr
cj3vvprwg00003i5x4lpggym4	cj3stwxsj00053i60her1775v
cj3vvprwg00003i5x4lpggym4	cj3su0v9h000c3i60ajxotps5
cj3vvprwg00003i5x4lpggym4	cj3su3phl000e3i6035mr5l6h
cj3vvpeax0000315lxxw93kys	cj3vvwfs70001265ewjw0d1no
cj3vvprwg00003i5x4lpggym4	cj3su164l000d3i60dt0srq97
cj3vvprwg00003i5x4lpggym4	cj3su0jye000b3i607noyybc7
cj3vvprwg00003i5x4lpggym4	cj3su4et4000f3i60e3wu4x9w
82	cj3su9bqa00003i60o3s8ri2m
cj3vvvr2f00043i5xdcdfxyuz	cj3vvw8uu00053i5x3djwsp3s
cj3vvprwg00003i5x4lpggym4	cj3su087a000a3i60qifunrxj
cj3vvprwg00003i5x4lpggym4	cj3styv1d00083i60iz4375ka
cj3vvqv1q00013i5x1pv6w796	cj3vvrx5g00033i5xt3pobe8v
cj3ybchm700012z63rsd494sg	cj3ykgwdq0000315ongzowvwb
cj3ybdg1i00042z633t79imgv	cj3ykyqk300002z63y0ytul5d
cj3ybdg1i00042z633t79imgv	cj3yl109s0004315ok3xv68fv
cj3ybcmsg00022z63d6q4aml4	cj3yl1ejf00022z63gg8tp7fe
cj3ybchm700012z63rsd494sg	cj3yoh6dn0005315ovicjvk5r
cj3yoi31i0006315oiavnc2c0	cj3yojrmk000c315op7d3rtv2
cj3um1x8a00042z5kuyadylsh	cj3vymxdu00022z5zu8ecbkeb
cj3um0lox00002z5kkoovo1ca	cj3vymxdu00022z5zu8ecbkeb
204	cj3wrzvdz00013e4ys14vp22i
cj3wrzvdz00013e4ys14vp22i	cj3wrzz1x00023e4yt7xx77lf
2383	cj3ws6oiw00033e4yvn1fx5h0
892	cj3x16hwe00002y5l0dffj95v
cj3yojrmk000c315op7d3rtv2	cj3yojwwv000d315owvzisn94
cj3ybberl00002z63a62bstta	cj3yol4zl00002z63xs0bgvp3
cj3yoldvm00012z63hxrqt2yf	cj3yolsjb00022z63rqm18xfx
cj3yol4zl00002z63xs0bgvp3	cj3yoldvm00012z63hxrqt2yf
cj3yoldvm00012z63hxrqt2yf	cj3yonipk00042z639jbpw9oe
cj3y9ooxp0000315o2urjuxc3	cj3yooieh0000315o5ymc17d2
cj3ybberl00002z63a62bstta	cj3yooieh0000315o5ymc17d2
cj3yooieh0000315o5ymc17d2	cj3yoqqv00002315ojwn1kxjv
cj3yoqqv00002315ojwn1kxjv	cj3yoqxi80003315o2e4lz2gu
cj3you64z0004315o72udoxut	cj3youfci0005315owjqlj8o4
cj3you64z0004315o72udoxut	cj3youxlg0007315o4ygsgs1w
cj3yow8b00008315owqxdjqun	cj3yowamx0009315o3brhe0vn
cj3yolsjb00022z63rqm18xfx	cj3youmtx00022z63655c9sj0
cj3yoldvm00012z63hxrqt2yf	cj3yoq2wi00012z6313klqyju
cj3yoq2wi00012z6313klqyju	cj3yoqnf400022z636amvyizf
cj3yolsjb00022z63rqm18xfx	cj3yotvxc00002z63dowma3fo
cj3yow8b00008315owqxdjqun	cj3yowji5000a315ofme37z47
cj3yowszj000b315ods4mnj8l	cj3yoxbdj000c315oag9a73uk
cj3yow8b00008315owqxdjqun	cj3yoxdqc000d315o8jtmwqlf
cj3yow8b00008315owqxdjqun	cj3yoxkdp00022b5mi9cdiws3
cj3yow8b00008315owqxdjqun	cj3yoy8wt00042b5mslzlnn2q
cj3yow8b00008315owqxdjqun	cj3yoyr50000e315oxdelpoqy
cj3yoq2wi00012z6313klqyju	cj3yozfn200012z63seew176g
cj3yolsjb00022z63rqm18xfx	cj3yp5vss00032z63hhqb05b4
cj3yooieh0000315o5ymc17d2	cj3yp7q3q000g315ouc4b01r6
cj3yoq2wi00012z6313klqyju	cj3ypaft000072z63emx3c5rr
cj3yooieh0000315o5ymc17d2	cj3ypd44q000k315okzr0ty1j
cj3ypdjgp000m315ozyqyty8p	cj3ypduwk000o315o223istw5
cj3ypdjgp000m315ozyqyty8p	cj3ypdzl8000p315o63tu2rjh
cj3ypdjgp000m315ozyqyty8p	cj3ype2wk000q315ouwxnli4v
cj3ypdjgp000m315ozyqyty8p	cj3ype5sf000r315oi6jplfjd
cj3ypdjgp000m315ozyqyty8p	cj3ypecz6000s315o5nf6k5ow
cj3ypecz6000s315o5nf6k5ow	cj3ype5sf000r315oi6jplfjd
cj3ypdjgp000m315ozyqyty8p	cj3ypemy200062b5m1xez9fvb
cj3umu1hf00002z5k2fx0rhr3	cj3yxpdsk00002y5ozi70x96w
cj3z0pgqw0000315orq2w1wcq	cj3z0pl1b0001315orbt5374l
cj3z0pgqw0000315orq2w1wcq	cj3z0pnte0002315ox1zoq7k0
cj3z0pgqw0000315orq2w1wcq	cj3z0pq3l0003315ojtlcgoxo
cj3z0pl1b0001315orbt5374l	cj3z0pnte0002315ox1zoq7k0
cj3x4n3ry00043k5kxem8f6k9	cj3zixwju00023k5ncv1j2t4w
cj41w0zwg0000315oy8uph3b2	cj41y9u12000b315oxw1beii6
cj3y9ooxp0000315o2urjuxc3	cj3z17ige00012y5ohic1gref
cj3z17ige00012y5ohic1gref	cj3z1aign00022y5oqgwmizia
cj3z1aign00022y5oqgwmizia	cj3z1avwp00032y5oib91irwn
cj3z17ige00012y5ohic1gref	cj3z1badj00042y5o7x4tffag
cj3z17ige00012y5ohic1gref	cj3z1bo4300052y5ojmmpa60e
cj3z17ige00012y5ohic1gref	cj3z1c2w900062y5onte6yzwg
cj3z17ige00012y5ohic1gref	cj3z1d5e700072y5o1vv10juw
cj3z17ige00012y5ohic1gref	cj3z1e0vm00092y5obuks7pl0
cj41w0zwg0000315oy8uph3b2	cj41xv8g90002315oo7hjtvlo
cj41w0zwg0000315oy8uph3b2	cj41xvalb0003315oy5n5zzbe
cj41w0zwg0000315oy8uph3b2	cj41xw1b20009315o3lgzpied
cj3z17ige00012y5ohic1gref	cj3z1dmr100082y5oayyzfvl7
82	cj3z25a980004315oks732tv4
82	142
cj41xve5g0006315ocmvbb5l6	cj41xw1b20009315o3lgzpied
cj41vb7x900042y5okrpymy6s	cj41vcek600072y5o42mphsii
82	cj41vvvxp0000315opl18wk3g
821	cj41z6yg900011w5wsbnkcd0b
293	cj40bygmn00003i7tv748iwio
293	385
cj41w0zwg0000315oy8uph3b2	cj41xvef50007315o0nmrbl2v
cj41w0zwg0000315oy8uph3b2	cj41xvf6a0008315obfgpb8hw
cj40bygmn00003i7tv748iwio	cj41z7kp300081w5w4vke15b3
cj41z7kp300081w5w4vke15b3	cj41z7jq600071w5wy1agk0k8
cj41z7jq600071w5wy1agk0k8	cj41z7kp300081w5w4vke15b3
cj40bygmn00003i7tv748iwio	cj41z81ab00091w5w1fblhgxi
cj40bygmn00003i7tv748iwio	cj41z8497000c1w5wvcff56bi
cj40bygmn00003i7tv748iwio	cj41z84wl000d1w5wak4waew8
cj41z8497000c1w5wvcff56bi	cj41z84wl000d1w5wak4waew8
cj41z8497000c1w5wvcff56bi	cj41z876h000f1w5w47y9qois
cj41z876h000f1w5w47y9qois	cj41z8993000g1w5wy0d2zy0x
cj41z89zy000h1w5wjyo71991	cj41z8aov000i1w5wd0yzrjhw
cj41z89zy000h1w5wjyo71991	cj41z8ben000j1w5w6e2jgz5h
cj41z89zy000h1w5wjyo71991	cj41z8bx0000k1w5wha818za2
cj41vb7x900042y5okrpymy6s	cj41vc24w00062y5o9110r7tp
cj41vczrr00082y5olai84qsl	cj41vd53i00092y5o8ksyqfgf
cj41w0zwg0000315oy8uph3b2	cj41xv4iy0000315odxh85ov0
cj41w0zwg0000315oy8uph3b2	cj41xvdl10004315oyop8xs77
cj41w0zwg0000315oy8uph3b2	cj41xvdvm0005315okbqqxedn
cj41w0zwg0000315oy8uph3b2	cj41xve5g0006315ocmvbb5l6
cj41xve5g0006315ocmvbb5l6	cj41xvalb0003315oy5n5zzbe
cj41xve5g0006315ocmvbb5l6	cj41xv8g90002315oo7hjtvlo
cj41xve5g0006315ocmvbb5l6	cj41xvef50007315o0nmrbl2v
cj41xve5g0006315ocmvbb5l6	cj41xvdvm0005315okbqqxedn
cj41xve5g0006315ocmvbb5l6	cj41xv4iy0000315odxh85ov0
cj41xve5g0006315ocmvbb5l6	cj41y9sef000a315odcjnpden
293	cj41z6fri00001w5w4tbubb74
cj41z6fri00001w5w4tbubb74	cj3pzlyg700003k5kpw5yfv46
cj3pzmbc700083k5k59aahdsp	cj3pzm2mn00013k5kje2qc99s
293	cj41z71pt00021w5wzf0mts6v
293	cj41z79bf00041w5wkfogasxx
293	cj41z7a1000051w5w6d6jt0n8
cj40bygmn00003i7tv748iwio	cj41z82us000a1w5wnmg5g4mh
cj41z84wl000d1w5wak4waew8	cj41z861o000e1w5wocy9etxg
cj41z89zy000h1w5wjyo71991	cj41z8ch5000l1w5wd5uz6ryi
cj3pzlyg700003k5kpw5yfv46	cj41z6fri00001w5w4tbubb74
cj3pzm2mn00013k5kje2qc99s	cj3pzlyg700003k5kpw5yfv46
293	cj41z77iu00031w5wdv3q66me
293	cj41z7aqj00061w5wdepdt2nw
cj40bygmn00003i7tv748iwio	cj41z83m2000b1w5wg46018co
293	cj41zl3h700002y5o51lf2q9f
cj41zl56y00012y5o4ms1txo3	cj41zl83p00042y5ouyt889xe
cj41zl56y00012y5o4ms1txo3	cj41zlbcw00082y5oz3tatntp
cj41zl56y00012y5o4ms1txo3	cj41zlc5u00092y5omctv6q5e
cj41zl56y00012y5o4ms1txo3	cj41zl67800022y5oa9j159ng
cj41zl56y00012y5o4ms1txo3	cj41zl8va00052y5or0rldcl9
cj41zl56y00012y5o4ms1txo3	cj41zl75f00032y5oq1izxu61
cj41zl56y00012y5o4ms1txo3	cj41zlacz00072y5oyq4tk5pd
cj41voc2200012y5oq4k0dbpc	cj41zl9gn00062y5o5htl4934
cj41vb7x900042y5okrpymy6s	cj41vbuqp00052y5orxbm8bk8
82	cj41zkbom00002z5nlnrvrj77
cj41zkbom00002z5nlnrvrj77	cj3yb1djc00002z63en1rgwk7
cj3yakrq900002d5mye06dlwb	cj41zlsx000012z5nm2ok51id
cj41zkbom00002z5nlnrvrj77	cj3yasiso00032d5mx3g4ifw7
cj41zkbom00002z5nlnrvrj77	cj3ya8iep000a2z639foa1ym9
cj41zkbom00002z5nlnrvrj77	cj3z25a980004315oks732tv4
293	cj41zoh8w000a2y5o3egljeuf
293	cj41zoj1e000b2y5oa5rroymg
293	cj41zoktl000c2y5omqj61qb1
cj41zkbom00002z5nlnrvrj77	cj3su9bqa00003i60o3s8ri2m
cj41z81ab00091w5w1fblhgxi	cj41z861o000e1w5wocy9etxg
cj41z8aov000i1w5wd0yzrjhw	cj41z89zy000h1w5wjyo71991
cj41zkbom00002z5nlnrvrj77	cj3ybr12c00022d5mt4g79cpe
cj41zkbom00002z5nlnrvrj77	cj3yaln6j00012d5mtmsfeka2
812	cj3pzlyg700003k5kpw5yfv46
cj41z6yg900011w5wsbnkcd0b	812
82	cj41zqeg800001w5w444yxk9b
cj41zkbom00002z5nlnrvrj77	cj3ybjhoq00012d5mk33vq5ii
82	cj41zqhha00011w5wfqggb6gb
812	cj41zqkxm00021w5wonk5lmz5
82	712
821	cj41zu1p4000d2y5olsa41eh2
821	cj41zu4gt000e2y5o02taqw7a
821	cj41zu77x000f2y5opsurpr5a
cj41zu77x000f2y5opsurpr5a	cj41zu1p4000d2y5olsa41eh2
cj41zu4gt000e2y5o02taqw7a	cj41zu1p4000d2y5olsa41eh2
821	cj41zumfx000i2y5ok0htul1q
cj41zumfx000i2y5ok0htul1q	cj41zuko8000h2y5otc7aadgr
821	cj41zuuxm000j2y5otr23qsh9
82	cj3yay9eg00042d5mrxqt9giu
cj41zkbom00002z5nlnrvrj77	cj3yay9eg00042d5mrxqt9giu
cj4206bks00032y5obpmk94mu	cj420781800042y5o4r69oxxq
cj4206bks00032y5obpmk94mu	cj4207f3z00052y5oufn4m1a0
cj434jmx900002y5ocxsusnsl	cj434kidc00012y5o3krnshwc
293	cj441o5it00002z5n3u00e75s
cj3u3bmyw00003k5krhjpu9gp	cj4444owi00003k5nj5lii8ok
cj4444owi00003k5nj5lii8ok	cj3u3dlmo00013k5kpv3a4ods
cj4444owi00003k5nj5lii8ok	cj3u3g2t400063k5k842tnha3
cj4444owi00003k5nj5lii8ok	cj3u3k5fq000c3k5kuerpvpkr
cj3u3bmyw00003k5krhjpu9gp	cj444p6rk00013k5ngce5tpit
cj444p6rk00013k5ngce5tpit	cj444psd400023k5nmdtplsu7
cj444psd400023k5nmdtplsu7	cj444q1a000033k5nitov4j9a
cj444psd400023k5nmdtplsu7	cj444q3e700043k5nx0u2euzh
cj444psd400023k5nmdtplsu7	cj444q98v00053k5nq7uf7jt2
cj444psd400023k5nmdtplsu7	cj444qhkv00063k5nfuwjdt3p
cj444qqh200073k5nb95ehjay	cj444qvlb00083k5nqfre03bu
cj444qqh200073k5nb95ehjay	cj444qy8f00093k5n6enn5ugm
cj444qqh200073k5nb95ehjay	cj444r8ji000a3k5n3dme7yv2
cj444qqh200073k5nb95ehjay	cj444rdfz000b3k5nxlt0enpc
cj444rdfz000b3k5nxlt0enpc	cj444rjgu000c3k5nexybg1n0
cj444rdfz000b3k5nxlt0enpc	cj444rn1i000d3k5nyu0zzqjn
cj444rdfz000b3k5nxlt0enpc	cj444rrda000e3k5ng1iiwdr6
cj444qqh200073k5nb95ehjay	cj444tdvg000f3k5n30jthq8j
cj444psd400023k5nmdtplsu7	cj444tncj000g3k5nr7hn17h0
cj444tncj000g3k5nr7hn17h0	cj444trnv000h3k5nwsnyeiyy
cj444tncj000g3k5nr7hn17h0	cj444u6ub000i3k5nubryy020
cj444p6rk00013k5ngce5tpit	cj444ud1f000j3k5n27rhhohz
cj444ud1f000j3k5n27rhhohz	cj444ui2a000k3k5nieuhtfd1
cj444tncj000g3k5nr7hn17h0	cj444us42000l3k5n5f3mmgr3
cj444tncj000g3k5nr7hn17h0	cj444utdf000m3k5ne21r895r
cj444tncj000g3k5nr7hn17h0	cj444uuki000n3k5ntbecxjqv
cj444tncj000g3k5nr7hn17h0	cj444uxia000o3k5n9crfym40
cj444tncj000g3k5nr7hn17h0	cj444uywh000p3k5nk4gyrkt7
cj444qqh200073k5nb95ehjay	cj444v7nu000q3k5n9rr2qnga
cj444qqh200073k5nb95ehjay	cj444vgmp000r3k5nldgks7bg
cj444qqh200073k5nb95ehjay	cj444vsf5000s3k5nwokrn82u
cj444ud1f000j3k5n27rhhohz	cj444wbjt000t3k5n7gphimvb
cj444qqh200073k5nb95ehjay	cj4455vbw000u3k5n5v3vauzz
cj4455vbw000u3k5n5v3vauzz	cj44567wz000v3k5nhugxcfcu
cj444qqh200073k5nb95ehjay	cj4457w9y000w3k5n1ix4vo5x
cj444tncj000g3k5nr7hn17h0	cj445l8ox000z3k5nslllxgzw
cj444tncj000g3k5nr7hn17h0	cj445li6p00103k5nij3bqzw2
cj444tdvg000f3k5n30jthq8j	cj444qqh200073k5nb95ehjay
82	cj445ofz100113k5nw3irgj7s
cj444ud1f000j3k5n27rhhohz	cj445s3uf00123k5ns9wcrzll
cj444ud1f000j3k5n27rhhohz	cj445sa3a00143k5nou2f9thn
cj444ud1f000j3k5n27rhhohz	cj445scrq00153k5nilefkdjl
cj444ud1f000j3k5n27rhhohz	cj445tl4c00183k5n0dzbfvpg
cj445tl4c00183k5n0dzbfvpg	cj445thys00173k5nzo3r1aug
cj445tl4c00183k5n0dzbfvpg	cj445swjx00163k5nlefnvz11
cj445tl4c00183k5n0dzbfvpg	cj445s79q00133k5nbhsvrjgy
cj444ud1f000j3k5n27rhhohz	cj445u3mc00193k5n9nqntirc
cj444psd400023k5nmdtplsu7	cj445uqoj001a3k5n3xq6e4gn
cj445uqoj001a3k5n3xq6e4gn	cj445uyj7001b3k5noz4962fc
cj445uqoj001a3k5n3xq6e4gn	cj445v3rv001c3k5n523v8y7s
cj445uqoj001a3k5n3xq6e4gn	cj445v8ga001d3k5n7ve4v6qb
cj445uqoj001a3k5n3xq6e4gn	cj445vjcq001e3k5nho1bx9fm
cj445v3rv001c3k5n523v8y7s	cj445xqy0001h3k5nx3jcyzgf
cj445v3rv001c3k5n523v8y7s	cj445y0u0001i3k5nw1yw180g
cj444trnv000h3k5nwsnyeiyy	cj445ziy6001m3k5n4sueohrf
cj444tncj000g3k5nr7hn17h0	cj445zsja001n3k5ndr6ic741
cj445ziy6001m3k5n4sueohrf	cj445z0dk001l3k5npi09pyt1
cj445ziy6001m3k5n4sueohrf	cj445yps0001j3k5nbejesrc9
cj445ziy6001m3k5n4sueohrf	cj445w3rv001f3k5nuje3ftuy
cj445ziy6001m3k5n4sueohrf	cj445w66z001g3k5nm4b9k561
cj445ziy6001m3k5n4sueohrf	cj445yrsf001k3k5no6wtj9iq
cj445ziy6001m3k5n4sueohrf	cj44610ro001o3k5nrq5puuz2
cj445ziy6001m3k5n4sueohrf	cj44619m3001p3k5novf91zml
cj445uqoj001a3k5n3xq6e4gn	cj4461kbo001q3k5nv54lmgf0
cj445uqoj001a3k5n3xq6e4gn	cj4461tsa001r3k5nmedgl13e
cj444ud1f000j3k5n27rhhohz	cj44654mu001s3k5nl1o06jy6
cj445uqoj001a3k5n3xq6e4gn	cj4465fbx001t3k5nlw25s3ji
cj445uqoj001a3k5n3xq6e4gn	cj446sg32001u3k5n27hq2cze
cj446sg32001u3k5n27hq2cze	cj446u37y001x3k5nk9ssylw2
cj446sg32001u3k5n27hq2cze	cj446vene001z3k5npsz4adgx
cj446sg32001u3k5n27hq2cze	cj446vneq00203k5n5lnad1mt
cj446sg32001u3k5n27hq2cze	cj446xymg00213k5nmuwu82jj
cj446xymg00213k5nmuwu82jj	cj446y9jk00223k5nlklycx8j
cj446xymg00213k5nmuwu82jj	cj446yezs00233k5nwowgv617
cj3u3bmyw00003k5krhjpu9gp	cj444qqh200073k5nb95ehjay
cj44fe6dt00002y5ouelvvui1	cj3yxpdsk00002y5ozi70x96w
cj44ff0xm00012y5o3hdw1cgs	268
cj44ff0xm00012y5o3hdw1cgs	387
cj441o5it00002z5n3u00e75s	cj44fl5fi00002y5o96zk0wc5
cj3umm81800012z5kb0afyub3	cj44fl5fi00002y5o96zk0wc5
cj44fnwvu00002y5oozvbzivt	cj44fou2800012y5oclazxqe4
cj44fnwvu00002y5oozvbzivt	cj44g3iky0001315o50qz7jsu
cj44g3gov0000315oe46f97bc	cj44g3kyj0002315ow3xvkerb
cj44g3iky0001315o50qz7jsu	cj44g3kyj0002315ow3xvkerb
cj3yb7nx400002d5medd8ezml	cj445ofz100113k5nw3irgj7s
1698	1700
1708	1958
1708	1966
1708	1984
1708	1735
1134	1698
cj45jh3ms00003k5nl42coy3j	cj45jj5tj00023k5nroug61y6
cj45jj5tj00023k5nroug61y6	cj45jl2wa00003i6393hvbov4
cj45jj5tj00023k5nroug61y6	cj45jl6yw00013i63jeg9e0cl
cj446sg32001u3k5n27hq2cze	cj446ttam001v3k5ng0dr045i
cj446sg32001u3k5n27hq2cze	cj446u0ty001w3k5n48m8nxvx
cj446sg32001u3k5n27hq2cze	cj446ub1x001y3k5nixdgz96u
cj445vjcq001e3k5nho1bx9fm	cj44763pu00243k5n0le6xd52
cj444p6rk00013k5ngce5tpit	cj444tdvg000f3k5n30jthq8j
cj41z7aqj00061w5wdepdt2nw	cj3pzm9a500063k5km8k1b1w7
cj41z7aqj00061w5wdepdt2nw	cj41zoh8w000a2y5o3egljeuf
cj41z7aqj00061w5wdepdt2nw	cj41zoj1e000b2y5oa5rroymg
cj44fe6dt00002y5ouelvvui1	cj3umu4lp00012z5k9dxhv6l7
cj44fnwvu00002y5oozvbzivt	cj44g3gov0000315oe46f97bc
cj441o5it00002z5n3u00e75s	cj44iq0v800013i63l8n1z7oo
cj3x4n3ry00043k5kxem8f6k9	cj3ylez4q00013f56rtnf9xvj
1708	1950
1708	2004
1708	1968
1708	2034
1708	2022
1708	1994
1735	2006
cj45jh3ms00003k5nl42coy3j	cj45jj2gn00013k5no8feremv
cj45jj5tj00023k5nroug61y6	cj45jk2my00033k5nmepmbp57
cj45jj5tj00023k5nroug61y6	cj45jl9ig00023i63mdll3jx5
cj45jj2gn00013k5no8feremv	cj45jmhif00043k5nrrgo2enm
cj45jmhif00043k5nrrgo2enm	cj45jmkbj00053k5n7kmahkex
cj45jmhif00043k5nrrgo2enm	cj45jmmwm00063k5n7io37r5p
cj45jk2my00033k5nmepmbp57	cj45jlhr500033i63leh02f9y
cj45jj5tj00023k5nroug61y6	cj45jn2lr00073k5ntkdaulp3
cj45jk2my00033k5nmepmbp57	cj45jn59300083k5n99c9kwaq
cj45jj2gn00013k5no8feremv	cj45jnyk600093k5n8vlblchw
cj45jnyk600093k5n8vlblchw	cj45jodvi000a3k5nlp8ni9ag
cj45jnyk600093k5n8vlblchw	cj45joop2000b3k5n91dnvkih
cj45jj5tj00023k5nroug61y6	cj45jpnwt000c3k5nkkox610s
cj45jj2gn00013k5no8feremv	cj45jptg4000d3k5npvkg3v17
cj45jl9ig00023i63mdll3jx5	cj45jqyr8000f3k5nbhdimcx0
cj45jh3ms00003k5nl42coy3j	cj45jr3it000g3k5nktwpuocm
cj45jl9ig00023i63mdll3jx5	cj45jr3it000g3k5nktwpuocm
cj45jl9ig00023i63mdll3jx5	cj45jrcos000h3k5nstsf1fao
cj45jj2gn00013k5no8feremv	cj45jsu84000i3k5nod8jrrn9
cj45jh3ms00003k5nl42coy3j	cj45ju1mt000j3k5napqxzzkc
cj45ju1mt000j3k5napqxzzkc	cj45jufn8000k3k5n7t4cad31
cj45ju1mt000j3k5napqxzzkc	cj45juvec000l3k5n1gbgsmbl
cj45ju1mt000j3k5napqxzzkc	cj45jv3l8000m3k5n2nhcxulm
cj45ju1mt000j3k5napqxzzkc	cj45jvik4000n3k5n4p05cntm
cj45jptg4000d3k5npvkg3v17	cj45jw3bf000o3k5nwf0f5lqf
cj45jptg4000d3k5npvkg3v17	cj45jwdez000p3k5np6yr9s0y
cj45jptg4000d3k5npvkg3v17	cj45jwwcu000q3k5nt70t596f
cj45jptg4000d3k5npvkg3v17	cj45jxuh2000r3k5nu498hsv0
cj45jxuh2000r3k5nu498hsv0	cj45jxyf1000s3k5nslpp1vuq
cj45jxuh2000r3k5nu498hsv0	cj45jy0qt000t3k5nw8o2psdo
cj45jxuh2000r3k5nu498hsv0	cj45jyccl000u3k5nrapa38bx
cj45jj2gn00013k5no8feremv	cj45jzkn8000v3k5ns7jm09aw
cj45jxuh2000r3k5nu498hsv0	cj45jq2nw000e3k5nlcgwiciy
cj45jj5tj00023k5nroug61y6	cj45k0xu1000w3k5n2txrqf36
cj45k0xu1000w3k5n2txrqf36	cj45k12bl000x3k5njv0rnr37
cj45k0xu1000w3k5n2txrqf36	cj45k165d000y3k5norkzmif9
cj45k0xu1000w3k5n2txrqf36	cj45k1afd000z3k5nsr49coi6
cj45jj2gn00013k5no8feremv	cj45k31a600113k5nmugr8y08
cj45jl2wa00003i6393hvbov4	cj45k4m0v00043i639dodyjr3
cj45jj2gn00013k5no8feremv	cj45kfq6i00123k5nt61ucm1x
cj45jh3ms00003k5nl42coy3j	cj45kk5ep00133k5nuch51jaq
cj45kk5ep00133k5nuch51jaq	cj45kka2p00143k5n4so53zbm
cj45kk5ep00133k5nuch51jaq	cj45kkdpc00153k5nh87eumev
cj45jh3ms00003k5nl42coy3j	cj45kl9ry00163k5n4zp57v54
cj45jh3ms00003k5nl42coy3j	cj45kvvtj00173k5nzeitlpa0
cj45kvvtj00173k5nzeitlpa0	cj45kw5zo00183k5ncg0fpl2a
cj45kvvtj00173k5nzeitlpa0	cj45kwcdf00193k5ndh0nqocg
cj45kk5ep00133k5nuch51jaq	cj45l5gyz001a3k5n1i5k8irp
cj45l5gyz001a3k5n1i5k8irp	cj45l5s42001b3k5nhblmvz9m
cj45jk2my00033k5nmepmbp57	cj45k2r3b00103k5nslbc4rdd
cj45zmr0600003k6219ggfj74	cj45zoqtc00003k60c7o2eg6e
cj45zoqtc00003k60c7o2eg6e	cj46l2ydl00013k5nurnw06fw
cj45zoqtc00003k60c7o2eg6e	cj46l329s00023k5n482sl6tf
cj45zoqtc00003k60c7o2eg6e	cj46l3s4m00033k5nv5p9xoes
cj45zoqtc00003k60c7o2eg6e	cj46lbm0o00043k5nyfouw3o7
cj46lbm0o00043k5nyfouw3o7	cj46lceun00053k5ngaljyobv
cj45zoqtc00003k60c7o2eg6e	cj46ldrc600063k5nvspxzdjg
cj46lepws00073k5n9suxptes	cj46lfi4z00083k5ni0rszo3e
cj45zmr0600003k6219ggfj74	cj46llkv600093k5nx3ohajpf
cj45zmr0600003k6219ggfj74	cj46llo0x000a3k5ncnbsk3tl
cj46llo0x000a3k5ncnbsk3tl	cj46llkv600093k5nx3ohajpf
cj46llo0x000a3k5ncnbsk3tl	cj46mgi90000b3k5ng1oma8h5
cj46llo0x000a3k5ncnbsk3tl	cj46mgl04000c3k5nmx058aua
cj46mgi90000b3k5ng1oma8h5	cj46mgtyz000d3k5nd2avp63x
cj46lbm0o00043k5nyfouw3o7	cj46mj5ob000e3k5n4pucde6z
cj46lbm0o00043k5nyfouw3o7	cj46ovq81000f3k5n4j1ioyi6
cj46lbm0o00043k5nyfouw3o7	cj46owu3x000g3k5nz0k9sive
cj46oxkr4000h3k5nvjdztcnb	cj46oxq14000i3k5ndf02k4ba
cj46owu3x000g3k5nz0k9sive	cj46lceun00053k5ngaljyobv
cj45zmr0600003k6219ggfj74	cj46peqle000j3k5nu68kiz8s
cj46owu3x000g3k5nz0k9sive	cj46ovq81000f3k5n4j1ioyi6
cj46lbm0o00043k5nyfouw3o7	cj46oxkr4000h3k5nvjdztcnb
cj46mgi90000b3k5ng1oma8h5	cj46pzgh90000265kdbn82vus
cj46mgl04000c3k5nmx058aua	cj46q469l0000265k8b24sua0
cj46mgi90000b3k5ng1oma8h5	cj470td0900003k5cjq7guk6k
cj47c283z00003k63wmi2t9tj	cj47c2b8100013k63kzaunblb
cj47c2b8100013k63kzaunblb	cj47c2fw900023k63f6o2yxi9
cj47c2b8100013k63kzaunblb	cj47c2j7t00033k632swx2y9p
cj47c2b8100013k63kzaunblb	cj47c2mgx00043k6331i2es0z
cj47c2b8100013k63kzaunblb	cj47c348p00053k63e8kuvszc
cj47c2b8100013k63kzaunblb	cj47c3m4800063k63uu7wct94
cj47c2b8100013k63kzaunblb	cj47c3o1b00073k63o15ewlq5
cj47c2fw900023k63f6o2yxi9	cj47c3v0f00083k63bpvdxxe1
cj47c2fw900023k63f6o2yxi9	cj47c433z00093k63ifq9bz05
cj47c2fw900023k63f6o2yxi9	cj47c47jb000a3k635b406i1z
cj47c2fw900023k63f6o2yxi9	cj47c4efq000b3k63ongr1v3a
cj47c2fw900023k63f6o2yxi9	cj47c4k1q000c3k6366ndez2r
cj47c283z00003k63wmi2t9tj	cj47c4tc7000d3k63hhf4hytp
cj47c4tc7000d3k63hhf4hytp	cj47c50qu000e3k63ornob9tc
cj47c4tc7000d3k63hhf4hytp	cj47c6cn0000h3k63ykkx9bwu
cj47c348p00053k63e8kuvszc	cj47c76nf000j3k63qxppgdo9
cj47c283z00003k63wmi2t9tj	cj47c8415000k3k6372d4oo9p
cj47c8415000k3k6372d4oo9p	cj47c8q94000m3k63pt64iwv7
cj47c8415000k3k6372d4oo9p	cj47c8vww000n3k63fdz0asz3
cj47c8q94000m3k63pt64iwv7	cj47c98s8000q3k63j3zxe72b
cj47c8q94000m3k63pt64iwv7	cj47can6e000w3k639ou0lupn
cj47c8vww000n3k63fdz0asz3	cj47cc3h8000x3k63u0t5ma7x
cj47c885l000l3k63kez4j537	cj47ccsde000z3k6358empyis
cj47c885l000l3k63kez4j537	cj47ccxkq00103k63t1gozz89
cj47cfa3z00133k63o13d5oyr	cj47cfez300143k63u0rkef7z
cj47chbbw00193k63gwomxa5u	cj47chhgs001a3k63adj2u4qs
cj47chbbw00193k63gwomxa5u	cj47chk7g001b3k63xisccdgi
cj47chbbw00193k63gwomxa5u	cj47cho4z001c3k636ts62nnp
cj47cdc6200113k636jn8o3ve	cj47cidw3001e3k63v2wrm15m
cj47cdc6200113k636jn8o3ve	cj47ci6jn001d3k63ree3bg4z
cj47cdc6200113k636jn8o3ve	cj47ciyrm001f3k63kg5m6unm
cj3z2w33r00012y5oi4luoeyn	cj3z178uv00002y5occ6exix2
cj47iimel00003i63ghtgg5ex	cj47ij5tj00013i63nnt6bc5c
cj47iimel00003i63ghtgg5ex	cj47ijpwe00043i63o3cvn96f
cj47iimel00003i63ghtgg5ex	cj47ijs1500053i63n820dep5
cj47iimel00003i63ghtgg5ex	cj47ijxdq00073i6331h3zdb4
cj47iimel00003i63ghtgg5ex	cj47ik07100083i63b4xaid1t
cj47c4tc7000d3k63hhf4hytp	cj47c55sd000f3k63nn5dtnv3
cj47c348p00053k63e8kuvszc	cj47c73ic000i3k63oapfvjsi
cj47c8vww000n3k63fdz0asz3	cj47c9kdj000t3k63he6i4ktr
cj47c885l000l3k63kez4j537	cj47ccnub000y3k63ax2658bh
cj47c4tc7000d3k63hhf4hytp	cj47cdc6200113k636jn8o3ve
cj47c283z00003k63wmi2t9tj	cj47cfa3z00133k63o13d5oyr
cj47c283z00003k63wmi2t9tj	cj47chbbw00193k63gwomxa5u
cj47c4tc7000d3k63hhf4hytp	cj47c61at000g3k633u7h20t6
cj47c283z00003k63wmi2t9tj	cj47c885l000l3k63kez4j537
cj47c8q94000m3k63pt64iwv7	cj47c948g000o3k6373fxlrpv
cj47c8q94000m3k63pt64iwv7	cj47c96lk000p3k633v4i0hda
cj47c8q94000m3k63pt64iwv7	cj47c9fbb000r3k63q6vid8wh
cj47c8vww000n3k63fdz0asz3	cj47c9ish000s3k63dfykh369
cj47c8vww000n3k63fdz0asz3	cj47c9lpj000u3k6384zt7ns3
cj47c8vww000n3k63fdz0asz3	cj47c9ozi000v3k633mfcwsxy
cj47c885l000l3k63kez4j537	cj47cenzk00123k63y6t51y2c
cj47cfez300143k63u0rkef7z	cj47cfo1b00153k63zi748r2o
cj47cfez300143k63u0rkef7z	cj47cfv2600163k63nzj0rwqo
cj47cfa3z00133k63o13d5oyr	cj47cg3g600173k63b5trw0zq
cj47c2b8100013k63kzaunblb	cj47cgc9h00183k63aiajm17x
cj47c283z00003k63wmi2t9tj	cj47d9432001g3k63h5hfj7av
cj47d9432001g3k63h5hfj7av	cj47d9dhh001h3k63rgehdu8c
cj47d9432001g3k63h5hfj7av	cj47da92k001i3k63o8688t99
cj47c283z00003k63wmi2t9tj	cj47dbsoq001j3k639zwgk6h2
cj47dbsoq001j3k639zwgk6h2	cj47dbw1e001k3k63hq3w0jre
cj47dbsoq001j3k639zwgk6h2	cj47dbzip001l3k63qnz9owtu
cj47d9432001g3k63h5hfj7av	cj47dc63t001m3k63twfrv0gl
cj47d9432001g3k63h5hfj7av	cj47dd46o001n3k63lc426lid
cj47dbsoq001j3k639zwgk6h2	cj47ddqjj001o3k63w2qk3tbo
cj47c4tc7000d3k63hhf4hytp	cj47dmaob001q3k63jj4sbpwy
cj47ik07100083i63b4xaid1t	cj47ijxdq00073i6331h3zdb4
cj47ijs1500053i63n820dep5	cj47ijuy700063i635qa4ipyq
cj47ijn5a00033i631mlon7am	cj47ijpwe00043i63o3cvn96f
cj47ijn5a00033i631mlon7am	cj47ijjms00023i63639a7mrv
cj47iimel00003i63ghtgg5ex	cj47inptk000a3i63hdf26uzm
cj47dbsoq001j3k639zwgk6h2	cj47djvpy001p3k63yonrq5dr
cj47iimel00003i63ghtgg5ex	cj47ijjms00023i63639a7mrv
cj47ijs1500053i63n820dep5	cj47ijpwe00043i63o3cvn96f
cj47iimel00003i63ghtgg5ex	cj47inxc8000b3i63himcjtf2
cj47iimel00003i63ghtgg5ex	cj47ijn5a00033i631mlon7am
cj47iimel00003i63ghtgg5ex	cj47iob3h000c3i6384a4rbft
cj47iob3h000c3i6384a4rbft	cj47inptk000a3i63hdf26uzm
cj47iob3h000c3i6384a4rbft	cj47inxc8000b3i63himcjtf2
cj47iimel00003i63ghtgg5ex	cj47ipe6j000d3i63v62vjplp
cj47iimel00003i63ghtgg5ex	cj47iph7f000e3i63wciaxikd
cj47ipe6j000d3i63v62vjplp	cj47iph7f000e3i63wciaxikd
cj47iimel00003i63ghtgg5ex	cj47ipljo000f3i63tcyr5r83
cj47ipe6j000d3i63v62vjplp	cj47ipljo000f3i63tcyr5r83
cj47iimel00003i63ghtgg5ex	cj47ips5e000g3i63qt4snml4
cj47ipe6j000d3i63v62vjplp	cj47ips5e000g3i63qt4snml4
cj47ijs1500053i63n820dep5	cj47ipe6j000d3i63v62vjplp
cj47iimel00003i63ghtgg5ex	cj47j09lg00001w5iu6uefyww
cj47iimel00003i63ghtgg5ex	cj47j1njk000h3i63cvvwjx97
cj47iimel00003i63ghtgg5ex	cj47j1kd600011w5if4cvhdr7
cj47iimel00003i63ghtgg5ex	cj47j1otj000i3i63btbku1db
cj47iimel00003i63ghtgg5ex	cj47j1q78000j3i636wgr73fq
cj47iimel00003i63ghtgg5ex	cj47j22q5000k3i63o5aioed5
82	cj47j7yrg00002y5ofkhoc3ag
82	cj47j8i2v00001w5w3plkndl9
cj3z2w33r00012y5oi4luoeyn	cj3yb7nx400002d5medd8ezml
82	cj47j9a3u00012y5ochzbuu14
cj47j9a3u00012y5ochzbuu14	cj3yddsmg00042d5mxsq2z7af
cj47j9a3u00012y5ochzbuu14	cj3ydhtzb00052d5moaqp04qm
cj3z2w33r00012y5oi4luoeyn	1351
cj47j9a3u00012y5ochzbuu14	cj44ip7j900003i63wwdm08oq
cj47j9a3u00012y5ochzbuu14	cj3yaocc800022d5mepewfqfy
82	cj47jakaz00022y5oxf3nt31h
82	cj47jan6b00032y5o80bar215
cj47jakaz00022y5oxf3nt31h	cj46lepws00073k5n9suxptes
cj47jan6b00032y5o80bar215	cj46lepws00073k5n9suxptes
cj47jakaz00022y5oxf3nt31h	cj45m0hsb00003k5nsb3da0oe
cj47jakaz00022y5oxf3nt31h	cj3vw56gl00003i5xex5h5kxf
82	cj47jb7ke00042y5otev7ci5o
cj47jb7ke00042y5otev7ci5o	cj47j7lqm00002y5o1qaqo33q
cj47j9a3u00012y5ochzbuu14	cj44fnwvu00002y5oozvbzivt
cj47j9a3u00012y5ochzbuu14	cj445a47p000y3k5n82kb5hq2
cj47j9a3u00012y5ochzbuu14	cj3yxsogq00012y5oj52653cm
cj48o50eb00002y5oi2f4g8j1	cj48nxtoz00003i5n11fju63i
cj48nxtoz00003i5n11fju63i	cj48nxw7y00013i5ngyp664gy
cj48nwrvl00003i5nrd4qsivr	cj48nxw7y00013i5ngyp664gy
cj48nwrvl00003i5nrd4qsivr	cj48oen2i00012y5oahvwm7fw
cj48swvum0000315oxnvy2c0m	cj48sxo6f0000315ouflqxjj1
cj48sxx600001315o8zfmph7a	cj48sz3610002315orwzn0t1w
cj48sz3610002315orwzn0t1w	cj48syjiy0001315oz6wxirqc
cj48sz3610002315orwzn0t1w	cj48syh9b0000315oajolbu3v
cj48sxx600001315o8zfmph7a	cj48t0f270003315o3zihfb5m
cj48sxx600001315o8zfmph7a	cj48t0hvw0004315o39ma1io7
cj48sxx600001315o8zfmph7a	cj48t0s9e0005315obantxrlg
cj48sxx600001315o8zfmph7a	cj48t1mnu0006315ogv0lr5mt
cj48t1mnu0006315ogv0lr5mt	cj48t415c0000315ov3jeedu1
cj48sxx600001315o8zfmph7a	cj48t6q1h0000315oi229beqz
cj48sxx600001315o8zfmph7a	cj48t77ax0001315o5jnz2ni0
cj48t77ax0001315o5jnz2ni0	cj48t9w2d0002315ol435k6m3
cj48t77ax0001315o5jnz2ni0	cj48ta8df0001315og6fcet3n
cj48sxx600001315o8zfmph7a	cj48tfv4u0002315o78t7dqnl
cj48t77ax0001315o5jnz2ni0	cj48tfcj000003u69kc4b5ij5
cj48sxx600001315o8zfmph7a	cj48tgi3300013u69k3bkpmdh
cj48sxx600001315o8zfmph7a	cj48th13a0003315ot679llkd
cj48sxx600001315o8zfmph7a	cj48tnnqh0003315oi3287olo
cj48tnnqh0003315oi3287olo	cj48ts9sq0008315og1946hxu
cj48ts9sq0008315og1946hxu	cj48trdqh0004315o1xsgqre7
cj48sxx600001315o8zfmph7a	cj48uswof0005315o4ot74lhp
cj48uswof0005315o4ot74lhp	cj48ut8wa0006315ocmug9c2z
cj48t9w2d0002315ol435k6m3	cj48uujdf0009315owc6rcel5
cj48t9w2d0002315ol435k6m3	cj48uunls000a315ogoux3h3e
cj48t9w2d0002315ol435k6m3	cj48uuqgt000b315ov2m8w7uw
cj48t9w2d0002315ol435k6m3	cj48uuxbp000c315oc9ayk7gi
cj48ta8df0001315og6fcet3n	cj48uv5hu000d315owboa5szm
cj48uveuj000e315ojp1dmhxk	cj48uvppf000f315ouu836vb1
cj48uveuj000e315ojp1dmhxk	cj48uw1p2000g315oz9wrk6oy
cj48uveuj000e315ojp1dmhxk	cj48t9fzr0000315ox5cg9ehd
cj48uveuj000e315ojp1dmhxk	cj48t9mue0001315okgwn8hb4
cj48uswof0005315o4ot74lhp	cj48uyg4d000h315obt74zhh0
cj48sxx600001315o8zfmph7a	cj48uzbhy000i315okiye7gfy
cj48uzbhy000i315okiye7gfy	cj48uyg4d000h315obt74zhh0
cj48uzbhy000i315okiye7gfy	cj48uv5hu000d315owboa5szm
cj48uzbhy000i315okiye7gfy	cj48uw1p2000g315oz9wrk6oy
cj48uzbhy000i315okiye7gfy	cj48uujdf0009315owc6rcel5
cj48uzbhy000i315okiye7gfy	cj48v0z7y000j315oxgmiq6ny
cj48v1kpu000k315ogrdvj2nk	cj48v25wv000l315orr5qmu7p
cj48t77ax0001315o5jnz2ni0	cj48v1kpu000k315ogrdvj2nk
cj48uzbhy000i315okiye7gfy	cj48v5gt7000m315ocs0yrow6
cj48t9w2d0002315ol435k6m3	cj48uveuj000e315ojp1dmhxk
cj48sxx600001315o8zfmph7a	cj48v988i000n315oysixrejl
cj48v988i000n315oysixrejl	cj48vb89k000p315ot5q795mm
cj48v988i000n315oysixrejl	cj48vbkr3000q315ownpo85nx
cj48v988i000n315oysixrejl	cj48v9eia000o315oiae2jmtm
cj48v988i000n315oysixrejl	cj48vcnaj000r315ojtqp9l21
cj48uzbhy000i315okiye7gfy	cj48vqpfr000s315oy3i86xg2
cj48uzbhy000i315okiye7gfy	cj48vqvqs000t315o7ofvrcc1
82	cj493huks00002y5ojgp40ibl
cj493huks00002y5ojgp40ibl	cj493i5iz00012y5ob9id32gd
82	cj49585c800002y5oihfkckmn
cj49590si00022y5og4h0o6e4	cj4959d9r00032y5o7zivoxrh
cj495c75o00042y5oaazmr04m	cj495es7500052y5otmf6wcqp
cj495c75o00042y5oaazmr04m	cj495evnd00001w5we8vrl4wz
cj49590si00022y5og4h0o6e4	cj495gcnx00002y5oclpkb4qc
cj495c75o00042y5oaazmr04m	cj495i3xd00001w5w5ith8b0e
cj49590si00022y5og4h0o6e4	cj495il3o00011w5wbh3zvrvd
cj47jb7ke00042y5otev7ci5o	cj495qy8j00021w5wm2tp0759
cj49ebyc700003r54enioonkz	cj441o5it00002z5n3u00e75s
82	cj49mcgq200002y5ok2bdw6wv
82	cj49ubsak00002z639irmsher
cj49ubsak00002z639irmsher	cj49uh6n300012z63ue72wg2d
cj49uh6n300012z63ue72wg2d	cj49uinp100032z63gaitvoe6
82	cj49uvf5p00042z63utsgbwgv
82	cj49v12ei00062z63xufkn3me
cj49uh6n300012z63ue72wg2d	cj49yplkn00002z62fsk1tvqc
cj49yplkn00002z62fsk1tvqc	cj49uhnek00022z63saylw2pe
82	cj49yzvsl00002z60uz46bpmt
cj3z2w33r00012y5oi4luoeyn	cj3yxsu1e00022y5oo17a4kxu
82	cj49z6fg600012z605xqf897j
cj4a1bnvh00001w6599vwaht5	cj4a1e1gd00011w659y8xl19c
cj4a1e1gd00011w659y8xl19c	cj4a1gaqf00031w65vos3r8eg
cj4a1e1gd00011w659y8xl19c	cj4a1hb5100041w65fedt6mwd
cj4a1el2o00021w65crdjv90c	cj4a1ko6s00001w63c4iuis0w
cj4a1e1gd00011w659y8xl19c	cj4a1el2o00021w65crdjv90c
cj4a1bnvh00001w6599vwaht5	cj4a1n80800001w63ow00fpt5
82	cj4abaa5400002y5obrbtl10w
82	cj4abg97z00012y5oj46r6wdm
cj4abg97z00012y5oj46r6wdm	cj4abhk1a00022y5octlcop01
82	cj4abibip00032y5od4vsxefj
82	cj4abj1h200042y5ozy0oox6o
cj4abg97z00012y5oj46r6wdm	cj4ablk6300052y5od4ezqw64
cj4ablk6300052y5od4ezqw64	cj4abm7nv00062y5oiev6u7fb
cj4abhk1a00022y5octlcop01	cj4abn1z900072y5o8rn3j4qf
cj4abhk1a00022y5octlcop01	cj4abnd3g00082y5o5up5giyp
cj4abhk1a00022y5octlcop01	cj4abnnax00092y5otq29kicy
cj4abg97z00012y5oj46r6wdm	cj4aboqc9000a2y5o17yu6fy8
cj4aboqc9000a2y5o17yu6fy8	cj4aboxfu000b2y5otl3e4103
cj4abg97z00012y5oj46r6wdm	cj4abq2uf00002y5ofs94wkby
cj4abhk1a00022y5octlcop01	cj4abtc6c00012y5o4rrodzqc
cj4abq2uf00002y5ofs94wkby	cj4abu12700022y5ocl9t1v80
cj4abq2uf00002y5ofs94wkby	cj4abuu2d00032y5o8gf56rw1
cj4abhk1a00022y5octlcop01	cj4abwsem00042y5o6tcm4qyq
cj4abu12700022y5ocl9t1v80	cj4abzcu100052y5ojivudnca
cj4abu12700022y5ocl9t1v80	cj4abzgml00062y5oay5fv96n
cj4abg97z00012y5oj46r6wdm	cj4abx9fm00001w631ix0vpxk
cj4abx9fm00001w631ix0vpxk	cj4ac08te00072y5o4xsrexz1
cj4abq2uf00002y5ofs94wkby	cj4ac1kwh00082y5o7dsjch56
cj4ac1kwh00082y5o7dsjch56	cj4ac1wv100092y5o5z0za7ck
cj4ac1kwh00082y5o7dsjch56	cj4ac2exl000a2y5ojr0qnrho
cj4ac5wpk000b2y5otoo9w19r	cj4ac685k000c2y5o6ymuytx3
cj4ac685k000c2y5o6ymuytx3	cj4ac702i000d2y5o7p554e8n
cj49mcgq200002y5ok2bdw6wv	cj4ac9zmq000e2y5oxc0vh7do
cj3ya7561000a315ohp0dibzt	cj3yd26sa0007315ofrkz826z
82	cj4acessc00002y5oxaokuf5e
cj3z2w33r00012y5oi4luoeyn	cj445ofz100113k5nw3irgj7s
82	cj4aci2ew00012y5oh5c5tqy3
cj48o50eb00002y5oi2f4g8j1	cj48nxyxp00023i5n432opq2a
cj48nxyxp00023i5n432opq2a	cj48o50eb00002y5oi2f4g8j1
cj47c283z00003k63wmi2t9tj	cj4b5wr4f00003k63yrk7i7nk
cj4b5wr4f00003k63yrk7i7nk	cj4b5x1m600013k63soae0o2q
cj4b5wr4f00003k63yrk7i7nk	cj4b5x57y00023k63y3v0oawn
cj4b8bw6y00002y5oweda5vrj	cj4b8c7tj00012y5okof2e5wa
cj4ac685k000c2y5o6ymuytx3	cj4bb404r00002m5sejovbnka
82	cj4bcn7cz00002y5o1wf1hp6q
cj4bcn7cz00002y5o1wf1hp6q	cj4bcnw3v00012y5o56nseyb0
cj4bcnw3v00012y5o56nseyb0	cj4bco0vi00022y5onsw1n194
cj4bcnw3v00012y5o56nseyb0	cj4bco66l00032y5okmx1vy08
cj4bcnw3v00012y5o56nseyb0	cj4bcobtn00042y5ocy2wcrp6
cj4bcobtn00042y5ocy2wcrp6	cj4bcojkf00052y5olyr1jv7s
cj4bcobtn00042y5ocy2wcrp6	cj4bcosc700062y5oua6d9jwg
cj4bcobtn00042y5ocy2wcrp6	cj4bcpuet00072y5ojcdghkg9
cj4bcobtn00042y5ocy2wcrp6	cj4bcqdha00082y5ovik6cr7p
cj4bcobtn00042y5ocy2wcrp6	cj4bcqn0p00002m5sjdnivj4y
cj4bcobtn00042y5ocy2wcrp6	cj4bcrloa00012m5s5982m4nq
cj4bcrloa00012m5s5982m4nq	cj4bcuuwd000a2y5o73i16evi
cj4bcrloa00012m5s5982m4nq	cj4bcwa13000b2y5o5cm2ixus
82	cj4bcxfga000c2y5oigpi388i
cj4bcrloa00012m5s5982m4nq	cj4bcvipe00022m5s5chb8r6c
cj4bcrloa00012m5s5982m4nq	cj4bcycxe000d2y5o65xqhgo1
cj4bcrloa00012m5s5982m4nq	cj4bcw82w00032m5s56mngtfp
cj4bcw82w00032m5s56mngtfp	cj4bcysxb000e2y5ou7cqgdem
cj4bcobtn00042y5ocy2wcrp6	cj4bd01ux000f2y5o7ieh62zy
cj4bd01ux000f2y5o7ieh62zy	cj4bd0jky000g2y5o212oye2l
cj4bd01ux000f2y5o7ieh62zy	cj4bd0ovs000h2y5o18kx7tu5
cj4bd0ovs000h2y5o18kx7tu5	cj4bd0tg8000i2y5or8gprm86
cj4bd0ovs000h2y5o18kx7tu5	cj4bd0w2c000j2y5ob99tdkm2
cj4bd0w2c000j2y5ob99tdkm2	cj4bd114n000k2y5o3dkb6o65
cj4bd0tg8000i2y5or8gprm86	cj4bd114n000k2y5o3dkb6o65
cj4bd01ux000f2y5o7ieh62zy	cj4bd1hl7000l2y5oh1a4b6z0
cj4bcobtn00042y5ocy2wcrp6	cj4bd26d3000m2y5o5xmtmzs0
cj4bd26d3000m2y5o5xmtmzs0	cj4bcukch00092y5ojhoatztl
82	cj4btfj7o00002y5o9vb2y5le
cj4abj1h200042y5ozy0oox6o	cj4btv5ji00002y5ojlumqtra
82	cj4bwc2nc00002y5owvb59y0v
cj47j9a3u00012y5ochzbuu14	cj4459gny000x3k5n066niblx
cj47j9a3u00012y5ochzbuu14	cj4bwqnx400002y5op7fjlbf6
cj3z2w33r00012y5oi4luoeyn	cj3yakrq900002d5mye06dlwb
82	cj4cxt1ox0003225o6ct6dgcd
cj4cxt1ox0003225o6ct6dgcd	cj4cxsw1o0001225o98hytphd
cj4cxsxx00002225odoucx7oe	cj4cxsqbz0000225o51p1e7pp
cj4cxt1ox0003225o6ct6dgcd	cj4cxsqbz0000225o51p1e7pp
cj4cxt1ox0003225o6ct6dgcd	cj4cxsxx00002225odoucx7oe
cj4ee6by200001w63lxrk03e0	cj4ee7kvo00061w63sp5y5yqh
cj4ee7kvo00061w63sp5y5yqh	cj4ee735t00051w63o4zv5r78
cj4ee7kvo00061w63sp5y5yqh	cj4ee6zol00041w63mgbb2iuo
cj4ee7kvo00061w63sp5y5yqh	cj4ee6rhf00011w63kwxbljno
cj4ee7kvo00061w63sp5y5yqh	cj4ee6v6w00021w63mp2ls0sh
cj4ee6by200001w63lxrk03e0	cj4ei5plm00002z5nup8jrxud
cj4ee6by200001w63lxrk03e0	cj4ei5sip00012z5nlgj5cogv
cj4ei5plm00002z5nup8jrxud	cj4ei5wpx00022z5nwk3cv0ed
cj4ei5plm00002z5nup8jrxud	cj4ei5zxh00032z5njbquweq8
cj4ei5plm00002z5nup8jrxud	cj4ei662t00042z5n6pnhkhg3
cj4ei5sip00012z5nlgj5cogv	cj4ei6ab600052z5neiltxzn5
cj4ee7kvo00061w63sp5y5yqh	cj4ee6xil00031w63ppmat88r
82	cj4eia3dj00062z5ny8h676xb
cj4ei5sip00012z5nlgj5cogv	cj4eid7nd00003i63e3xjxp7e
cj4eie34x00013i63slr8raom	cj4eiex0h00082z5ni9a9wrco
cj4ee7kvo00061w63sp5y5yqh	cj4eifbon00033i637y9ste9b
cj4eie34x00013i63slr8raom	cj4eig81a00092z5n09h9d0y1
cj4eie34x00013i63slr8raom	cj4eih2xq00043i63ku92v4x3
cj4eie34x00013i63slr8raom	cj4eihyml00063i63v3ui547w
cj4eie34x00013i63slr8raom	cj4eijgd9000a2z5n1tbpu5hj
cj4eie34x00013i63slr8raom	cj4eijx5e000b2z5nkh6gsiib
cj4eie34x00013i63slr8raom	cj4ejvck500073i6300js8sjq
cj4ee6by200001w63lxrk03e0	cj4eihnad00053i63l7j82r1m
cj4ee6by200001w63lxrk03e0	cj4eiki1v000c2z5nxn7ahzmv
cj46peqle000j3k5nu68kiz8s	cj4fovg8r00002366dzhwwyd2
82	cj4fpqdm50000316349ur4fsz
82	cj4fr74xv000031635ug6yv4q
82	cj4fr7hp600013163cxw8cub4
cj45zmr0600003k6219ggfj74	cj4ftbz270000295pk5f3n4hw
cj4qssxa000003c64szhnu3zr	cj4qst5yi00013c64rt7lrhf3
cj4qssxa000003c64szhnu3zr	cj4qstiss00033c64pae8def0
cj4qst5yi00013c64rt7lrhf3	cj4qsudao00053c64rfswbhq5
cj4qstiss00033c64pae8def0	cj4qsudao00053c64rfswbhq5
cj4qsu4h900043c644bvtu97g	cj4qstdup00023c64yta1gkx1
cj4qssxa000003c64szhnu3zr	cj4qt8ipk00003c64e7791iqh
cj4qt8ipk00003c64e7791iqh	cj4qstdup00023c64yta1gkx1
cj4qst5yi00013c64rt7lrhf3	cj4qstdup00023c64yta1gkx1
cj4qt8ipk00003c64e7791iqh	cj4qsu4h900043c644bvtu97g
cj4qsudao00053c64rfswbhq5	cj4qtbrwe00013c64sdy7ma2n
cj4qtbrwe00013c64sdy7ma2n	cj4qtbz0y00023c64nua2sdmt
cj4ymfzeu0000315ozejbabjt	cj4ymg2a40001315o2ud3zckr
cj4ymfzeu0000315ozejbabjt	cj4ymg4fj0002315o6f3xdgll
cj4ymg4fj0002315o6f3xdgll	cj4ymh5cl0003315ocn5yk4x9
cj4ymg2a40001315o2ud3zckr	cj4ymh5cl0003315ocn5yk4x9
cj4ymg4fj0002315o6f3xdgll	cj4ymhh4a0004315onbm9xmwk
cj4ymg4fj0002315o6f3xdgll	cj4ymhhw60005315ok1ckizf2
cj4ymg4fj0002315o6f3xdgll	cj4ymhiqn0006315o1cl8ki6l
cj4ymg2a40001315o2ud3zckr	cj4ymhkw80007315o1loxkein
cj4ymg2a40001315o2ud3zckr	cj4ymhlt20008315onhbk9oli
cj4ymg2a40001315o2ud3zckr	cj4ymhiqn0006315o1cl8ki6l
cj4ymg4fj0002315o6f3xdgll	cj4ymjm3o0009315oqrc5w7yn
cj4ymhlt20008315onhbk9oli	cj4ymkxoo000a315oljvg9ix1
cj4ymhlt20008315onhbk9oli	cj4yml8ps000b315o9lvnskhm
cj4abg97z00012y5oj46r6wdm	cj4zbdmnt00001y5j2l4o2mrk
cj51emfpk00003s4rn3f9mom1	cj51emoyx00013s4rmpshe0lw
cj51emfpk00003s4rn3f9mom1	cj51en4tz00023s4r6osriocd
cj51emfpk00003s4rn3f9mom1	cj51enlcs00033s4rpjvme9jv
cj51emoyx00013s4rmpshe0lw	cj51enupi00043s4r0fbddgin
cj51emfpk00003s4rn3f9mom1	cj51er3sj00053s4r90uvgql0
cj5fqira800003q681spcpcgc	cj5fqj3j000013q68oqy0dkui
cj5fqira800003q681spcpcgc	cj5fqk1x500023q68zb7uqnx4
cj5fqqi8f00003i62yg5t2vp4	cj5fqrybk00003i54faiiuhmf
cj5fqqi8f00003i62yg5t2vp4	cj5fqu5nj00023i62ao5invw8
cj5fqqi8f00003i62yg5t2vp4	cj5fqu8g100002z5ra2tvdm8z
cj5fqqi8f00003i62yg5t2vp4	cj5fquirz00033i62d7pcxgwb
cj5fqrybk00003i54faiiuhmf	cj5fqv70k00012z5rek9nme1r
cj5fqrybk00003i54faiiuhmf	cj5fqvwf100043i62ve0x7mt5
cj5fqqi8f00003i62yg5t2vp4	cj5fqz8x600003i62ni5a6uff
cj5fqqi8f00003i62yg5t2vp4	cj5fqzocs00022z5rfidhsg0k
cj5fqu8g100002z5ra2tvdm8z	cj5fqs0ti00013i5401q15yqk
cj5hcq13m0000315ohkm0bvfz	cj5hcq7o40001315ozs3k66uw
cj5hcq7o40001315ozs3k66uw	cj5hcqfqq0003315oz24sfenp
cj5hcq9rk0002315oo6nlrw04	cj5hcqfqq0003315oz24sfenp
cj5hcqfqq0003315oz24sfenp	cj5hcr16s0004315oek3phqhd
cj5hcq7o40001315ozs3k66uw	cj5hcswg40005315ovvq5720l
cj5hcswg40005315ovvq5720l	cj5hcq9rk0002315oo6nlrw04
cj5khaxje0000315oj5r7ywe6	cj5kigrvr0001315o7fr1lpq1
82	cj5kjqm2r0000315oaneej5pl
cj5khaxje0000315oj5r7ywe6	cj5ln04uy0000315nzf1cn0ic
cj5khaxje0000315oj5r7ywe6	cj5ln0him0000315oewok5r55
cj5khaxje0000315oj5r7ywe6	cj5ln0q9p0001315nnd99jjet
cj5khaxje0000315oj5r7ywe6	cj5ln0us10001315o8rhrljy7
cj5khaxje0000315oj5r7ywe6	cj5ln3fm10002315ng00j9aa8
cj5khaxje0000315oj5r7ywe6	cj5ln3ov00003315nsmq93zoc
cj5ln3ov00003315nsmq93zoc	cj5ln421z0004315n5oi8j2cf
cj5khaxje0000315oj5r7ywe6	cj5ln4l2g0005315n2w7nb9pm
cj5lpxla40002315oijhq4pln	cj5lpxiqw00022c5huhke1kdc
cj5lpxla40002315oijhq4pln	cj5lpv1yv0002315o5qpewjf7
cj5lq6yxp00072c5hx7h0z9nv	cj5lq744g0008315oxfaw6d5p
cj3pzmbc700083k5k59aahdsp	75
cj5lpv1yv0002315o5qpewjf7	cj5lpvcyj00012c5hhct4v34f
cj5ln0us10001315o8rhrljy7	cj5ln75ol000i315nldtoohgw
cj5ln0us10001315o8rhrljy7	cj5ln79a5000j315nn59exi8b
cj5ln0us10001315o8rhrljy7	cj5ln737z000h315nglqx4bhs
cj5lpv1yv0002315o5qpewjf7	cj5lpwcvm0000315ogbedui71
cj5lpsgvk00002c5he0mgppjq	cj5lpwtba0001315oknkkj4mn
cj5lpsgvk00002c5he0mgppjq	cj5lpxla40002315oijhq4pln
cj5lpsgvk00002c5he0mgppjq	cj5lpxo5w00032c5h6z172gsa
cj5lpxla40002315oijhq4pln	cj5lpycee0000315oo2qoxcsv
cj5lpv1yv0002315o5qpewjf7	cj5lpyzmh0001315o2pzpmlss
cj5lpsgvk00002c5he0mgppjq	cj5lq07o10002315oy6olanzq
cj5lq07o10002315oy6olanzq	cj5lq0fr400042c5h3v2z6jo3
cj5lpsgvk00002c5he0mgppjq	cj5lq38jq0006315oh3ihbpb3
cj5lq38jq0006315oh3ihbpb3	cj5lq30vi0004315oipx62m6e
cj5lq38jq0006315oh3ihbpb3	cj5lq33w60005315ome3xfh3d
cj5lq38jq0006315oh3ihbpb3	cj5lq2y0m0003315ow6s9qi5t
cj5lq38jq0006315oh3ihbpb3	cj5lq3iu600052c5hibx3p2hm
cj5lq38jq0006315oh3ihbpb3	cj5lq68nx0007315on3o48io7
cj5lpsgvk00002c5he0mgppjq	cj5lq6qca00062c5h4hkg8etg
cj5lpsgvk00002c5he0mgppjq	cj5lq6yxp00072c5hx7h0z9nv
cj5lq6yxp00072c5hx7h0z9nv	cj5lq7pmn0009315ozslgokmr
cj5lq6yxp00072c5hx7h0z9nv	cj5lq7hgb00082c5hj9sborqi
387	cj3pzmheu000b3k5k97eyqexk
371	337
82	cj4qssxa000003c64szhnu3zr
cj4abwsem00042y5o6tcm4qyq	cj445a47p000y3k5n82kb5hq2
cj5mrijzd00003c6hic6doada	cj5mriyox00013c6hv1okkwq5
cj5mrijzd00003c6hic6doada	cj5mrj8g800033c6hljoz4sx1
cj5mriyox00013c6hv1okkwq5	cj5mrjqpj00043c6hkw5b9cul
cj5mrj8g800033c6hljoz4sx1	cj5mrkatj00053c6hbbl3g3ni
cj5mrijzd00003c6hic6doada	cj5mrklvr00063c6hfm9n017u
cj5mrijzd00003c6hic6doada	cj5mrkqu700073c6hjtpuirgj
cj5mriyox00013c6hv1okkwq5	cj5mrli4v00083c6hksy58iu7
cj5mrijzd00003c6hic6doada	cj5mrj2io00023c6hwbkq3uwh
cj5mrijzd00003c6hic6doada	cj5mrq0xk000a3c6htxftpfj8
cj5mrq0xk000a3c6htxftpfj8	cj5mrq7fz000b3c6hver24fdv
cj5mrq0xk000a3c6htxftpfj8	cj5mrqnce000d3c6hbnsj6t3d
cj5mriyox00013c6hv1okkwq5	cj5mrqzrq000f3c6h1pnst8y9
cj5mriyox00013c6hv1okkwq5	cj5mrr1vy000g3c6hs4sq2ujs
cj5mrqepz000c3c6h23rppjgw	cj5mrqnce000d3c6hbnsj6t3d
cj5mrqnce000d3c6hbnsj6t3d	cj5mrqvza000e3c6h9cpibdvm
cj5mrpwq700093c6hxpvj2ayt	cj5mrqepz000c3c6h23rppjgw
cj5mriyox00013c6hv1okkwq5	cj5ms4jy5000h3c6h9xbrl18h
cj5mrq0xk000a3c6htxftpfj8	cj5mrqvza000e3c6h9cpibdvm
cj5mrpwq700093c6hxpvj2ayt	cj5ms4sst000i3c6h9bzklnm1
cj5mrijzd00003c6hic6doada	cj5mrpwq700093c6hxpvj2ayt
cj5mrpwq700093c6hxpvj2ayt	cj5mso1qr000l3c6hee3uu9lo
cj5mrijzd00003c6hic6doada	cj5mso1qr000l3c6hee3uu9lo
cj5mso1qr000l3c6hee3uu9lo	cj5msqu64000n3c6hqm7fdyv8
cj5msqu64000n3c6hqm7fdyv8	cj5msqpuk000m3c6h4dsaa4yq
cj5mso1qr000l3c6hee3uu9lo	cj5mszqu2000o3c6h2acqpgiv
cj5mt1j1e000q3c6hpn09570n	cj5mt1qbu000r3c6hntgduoms
cj5mt1j1e000q3c6hpn09570n	cj5mt1t9u000s3c6hfqrrq6ed
cj5mt1j1e000q3c6hpn09570n	cj5mt1ui2000t3c6ho58167oe
cj5mt1qbu000r3c6hntgduoms	cj5mt28q2000u3c6hgyaeyyyd
cj5mt1qbu000r3c6hntgduoms	cj5mt2iwa000v3c6h9qut0wk0
cj5mt1qbu000r3c6hntgduoms	cj5mt66pl000w3c6hok8i5tvy
cj5mt1qbu000r3c6hntgduoms	cj5mt677l000x3c6hfcozkwtg
cj5mt1qbu000r3c6hntgduoms	cj5mt67qh000y3c6h8e11bify
cj5mt1qbu000r3c6hntgduoms	cj5mt688h000z3c6h46zz033q
cj5mt1qbu000r3c6hntgduoms	cj5mt68qz00103c6hxy53pqt7
cj5mt1qbu000r3c6hntgduoms	cj5mt69ol00113c6hl6j0g765
cj5mt1qbu000r3c6hntgduoms	cj5mt69vs00123c6h4lai6kb3
cj5mt1qbu000r3c6hntgduoms	cj5mt6aca00133c6h074fjkmk
cj5mt1qbu000r3c6hntgduoms	cj5mt6apy00143c6huhpb3eq9
cj5mt1qbu000r3c6hntgduoms	cj5mt6b5p00153c6h9mzs38ko
cj5mt1qbu000r3c6hntgduoms	cj5mt6bjp00163c6hot1vq7nk
cj5mt1ui2000t3c6ho58167oe	cj5mt7xui00173c6hhbudartc
cj5mt66pl000w3c6hok8i5tvy	cj5mt7xui00173c6hhbudartc
cj64zz7pd0000205wkxyiuayy	cj6500zl40001205wzrkuxb8t
cj64zz7pd0000205wkxyiuayy	cj65015af0002205wmawhhrre
cj64zz7pd0000205wkxyiuayy	cj65018kn0003205wvrri9bp9
cj64zz7pd0000205wkxyiuayy	cj6501adz0004205w0tbzp0ti
cj64zz7pd0000205wkxyiuayy	cj6501cof0005205w7qrs5rue
cj64zz7pd0000205wkxyiuayy	cj6501i670006205wocooweap
cj6500zl40001205wzrkuxb8t	cj6503f470007205w7f6y0y5d
cj6500zl40001205wzrkuxb8t	cj6505dhp0008205wqdnqcfua
cj6500zl40001205wzrkuxb8t	cj65089ip0009205wkf4octzj
cj6501i670006205wocooweap	cj650an0p000a205w4uai5ks1
cj656ys5b0000305ooz7aap5a	cj656zgyf0001305os8fet4oo
cj656ys5b0000305ooz7aap5a	cj6570kvy0001305odt82xky6
cj656ys5b0000305ooz7aap5a	cj656zwhv0000305ojjzxgn87
cj656zwhv0000305ojjzxgn87	cj6570rr80002305oi5cvxn9o
cj656zwhv0000305ojjzxgn87	cj6570t0s0003305olj9b1m90
cj656zwhv0000305ojjzxgn87	cj6570ty40004305ohqqbvusl
cj6570rr80002305oi5cvxn9o	cj6577m7z0005305o8zmu918x
cj6570rr80002305oi5cvxn9o	cj6577ozb0006305oyww7wpca
cj659wims0000305om9r195mo	cj659wm9j0001305o880cus8v
337	cj3pzmbc700083k5k59aahdsp
cj6xbz6l300003i5ox3x7ihm5	cj6xbzicb00013i5oncmer98a
cj6xbzicb00013i5oncmer98a	cj6xbzy4300023i5otg53p4id
cj6xbzicb00013i5oncmer98a	cj6xc0a2200033i5o7093li32
cj6xbzicb00013i5oncmer98a	cj6xc0cmy00043i5o0wj6mpnu
82	cj6xc2mie00003i5ojigob5zf
82	cj6xc4b9k00013i5ohea1ci3l
cj77hpvv200003s66wopm8i84	cj77hwm5v00023x66x810d2bk
cj6xc8c9g00023i5o0c26uex2	cj6xc8yde00033i5oxprp2zbo
cj6xc8c9g00023i5o0c26uex2	cj6xc993t00053i5ooqvj7j79
cj6xc8c9g00023i5o0c26uex2	cj6xc9cr500063i5oylqpee6u
cj6xc8c9g00023i5o0c26uex2	cj6xc9e4x00073i5owuf3o74s
cj6xc8c9g00023i5o0c26uex2	cj6xcau0g00093i5o5vbigv9b
cj6xc8c9g00023i5o0c26uex2	cj6xc92tm00043i5om3avvto5
cj6xc8c9g00023i5o0c26uex2	cj6xcbkwf000a3i5om4ru1qye
cj6xcbkwf000a3i5om4ru1qye	cj6xcan9s00083i5ob2vf6b51
cj6xcbkwf000a3i5om4ru1qye	cj6xcc8t3000b3i5oxhvm08w9
cj6xcbkwf000a3i5om4ru1qye	cj6xccic6000c3i5otycddiwp
cj6xc8c9g00023i5o0c26uex2	cj6xccm1a000d3i5o36pyeu6k
cj6xc8c9g00023i5o0c26uex2	cj6xccse6000e3i5oukwqzgrr
cj6xc8c9g00023i5o0c26uex2	cj6xccupy000f3i5oqshz5evi
cj6xccm1a000d3i5o36pyeu6k	cj6xccz9q000g3i5o9uisr5el
cj6xccupy000f3i5oqshz5evi	cj6xccz9q000g3i5o9uisr5el
cj6xccse6000e3i5oukwqzgrr	cj6xccz9q000g3i5o9uisr5el
cj6xccm1a000d3i5o36pyeu6k	cj6xcdsvh000h3i5o53ybqwei
cj6xccm1a000d3i5o36pyeu6k	cj6xcdv3p000i3i5o9e4bdqh4
cj6xccm1a000d3i5o36pyeu6k	cj6xcdx4t000j3i5o7bq2epov
cj6xccse6000e3i5oukwqzgrr	cj6xcdv3p000i3i5o9e4bdqh4
cj6xccupy000f3i5oqshz5evi	cj6xcdx4t000j3i5o7bq2epov
cj6xccupy000f3i5oqshz5evi	cj6xcdv3p000i3i5o9e4bdqh4
cj6xc8c9g00023i5o0c26uex2	cj6xce911000k3i5oi8ea1lrq
cj6xce911000k3i5oi8ea1lrq	cj6xcdx4t000j3i5o7bq2epov
cj6xce911000k3i5oi8ea1lrq	cj6xcdsvh000h3i5o53ybqwei
cj6xc8c9g00023i5o0c26uex2	cj6xcf9ln000l3i5olneln26d
cj6xcf9ln000l3i5olneln26d	cj6xcdsvh000h3i5o53ybqwei
cj6xc8c9g00023i5o0c26uex2	cj6xcfmlu000m3i5ooimy102n
82	cj6xcixl0000n3i5oakr5mg46
82	cj46lfi4z00083k5ni0rszo3e
286	187
286	cj41z876h000f1w5w47y9qois
286	cj41z7jq600071w5wy1agk0k8
82	cj3umlabf00002z5k2phb1xic
82	cj3umpp1b00002z5kgh6juv0d
cj5nu9nn70000315s4hd1rfjc	cj72eh38o00013260smm1ljkw
cj72eh5f900023260fa9q2eic	cj72ehcqx00033260k3t5q0ub
337	cj3pzm79d00043k5km2l2pwc7
cj77hpvv200003s66wopm8i84	cj77hqm7m00013s664p0f6aw5
cj77hpvv200003s66wopm8i84	cj77hrm7700003x66s1ha7r0w
cj77hrwuy00023s663kyvv0bg	cj77hw5x000043s66sj8c9mfo
cj77hpvv200003s66wopm8i84	cj77hrwuy00023s663kyvv0bg
cj77htg6700033s66xdcf7h9e	cj77i5i6300073s66rkjeg0dc
cj78rq2jd00002y60gngh7nqg	cj78rqh2j00022y607s3n5mx4
cj78rqh2j00022y607s3n5mx4	cj78rr71x00052y60twri8ubu
cj78rqmzy00032y60vvzw6k84	cj78rrguy00062y60au4eklob
cj78ru5bd00072y60wt82tfxl	cj78rqusw00042y60rhxwctqn
82	cj78rzzik00082y60d61718p1
82	cj790mnvk00002z62na6itf1r
cj7983b7a00042z5okzh1dmbi	cj7986gh700001w5lnyxmhtdx
cj77hrwuy00023s663kyvv0bg	cj77htg6700033s66xdcf7h9e
cj77hrwuy00023s663kyvv0bg	cj77ht9q100013x66g1b8ysec
cj77htg6700033s66xdcf7h9e	cj77i1c4i00063s66g5stoga6
cj77htg6700033s66xdcf7h9e	cj77hxxy700053s66roampaaf
cj78rq2jd00002y60gngh7nqg	cj78rqf8c00012y60edwsjw69
cj78rq2jd00002y60gngh7nqg	cj78rqmzy00032y60vvzw6k84
cj78rqf8c00012y60edwsjw69	cj78rqusw00042y60rhxwctqn
cj78rq2jd00002y60gngh7nqg	cj78ru5bd00072y60wt82tfxl
cj78ru5bd00072y60wt82tfxl	cj78rrguy00062y60au4eklob
cj78ru5bd00072y60wt82tfxl	cj78rr71x00052y60twri8ubu
82	cj78s50cs00092y609ffvgzby
cj79812kb00002z5ol979v48q	cj7981p4q00012z5o4y5769dx
cj7981p4q00012z5o4y5769dx	cj7981r4k00032z5o1g1jnfq1
cj7981p4q00012z5o4y5769dx	cj7981qoa00022z5oae4ey4x9
cj79812kb00002z5ol979v48q	cj7983b7a00042z5okzh1dmbi
cj7983b7a00042z5okzh1dmbi	cj7983dkf00052z5otv0sekl5
cj7983b7a00042z5okzh1dmbi	cj7984hlm00082z5oclcbenon
cj79812kb00002z5ol979v48q	cj798423c00072z5oromtc800
cj7983b7a00042z5okzh1dmbi	cj7983fr300062z5ofi0rrli1
cj7983b7a00042z5okzh1dmbi	cj79865o700092z5onlnprda7
cj7983b7a00042z5okzh1dmbi	cj79868wh000a2z5obddizjdc
cj3um0lox00002z5kkoovo1ca	cj7g7sj3u00002y5o0be5zkyf
cj7g7sj3u00002y5o0be5zkyf	cj7g7sn1100012y5o8nsyt0lp
cj7g7sj3u00002y5o0be5zkyf	cj7g7soyj00022y5osxmt32jl
cj7g7sj3u00002y5o0be5zkyf	cj7g7srec00032y5owpfn7pwm
cj7jb5zkr00002y5pxmy2jk07	cj7jb6a6600012y5p1p5slruj
cj7jb5zkr00002y5pxmy2jk07	cj7jb77dn00062y5pmogoi2tu
cj7jb77dn00062y5pmogoi2tu	cj7jb79lz00072y5pffmuewa9
cj7jb77dn00062y5pmogoi2tu	cj7jb7dvi00092y5p83rjwmg3
cj7jb5zkr00002y5pxmy2jk07	cj7jb80um000a2y5p1cp2hmic
cj7jb5zkr00002y5pxmy2jk07	cj7jb9yp5000b2y5pnn3ltev4
cj7jb5zkr00002y5pxmy2jk07	cj7jbac4b00002y5pg2c7z6s3
cj7jb5zkr00002y5pxmy2jk07	cj7jbap4400002y5pvtonbq63
cj7jb6a6600012y5p1p5slruj	cj7jb6oki00042y5piwfqhzoq
cj7jb77dn00062y5pmogoi2tu	cj7jb6qwq00052y5pbis60q7a
cj7jb77dn00062y5pmogoi2tu	cj7jbbypz00012y5pe81xzjvr
cj7jb77dn00062y5pmogoi2tu	cj7jb6hgl00022y5py44bncem
cj7jb77dn00062y5pmogoi2tu	cj7jb7ckm00082y5pijvfcoon
cj7jb6a6600012y5p1p5slruj	cj7jcckq900012y5pafuf6gyp
cj7jb80um000a2y5p1cp2hmic	cj7jb6m9t00032y5plnkpcrbe
cj7jb77dn00062y5pmogoi2tu	cj7jccj0500002y5p19vtsm5x
268	cj40bygmn00003i7tv748iwio
cj5nu9nn70000315s4hd1rfjc	cj72eh5f900023260fa9q2eic
cj7hqm32600003i60do32gwik	cj7p5zo2100001w5lb8rus0x1
cj7hqm32600003i60do32gwik	cj7p5zpr900011w5livok13hi
cj7q0mac70000305pfziyd5nl	cj7q0qac70000305p7zql154d
cj7q0mac70000305pfziyd5nl	cj7q0qloh0000855dcasqochv
cj7q0mac70000305pfziyd5nl	cj7q0rjeg0001305p8nd6mdfz
cj7q0mac70000305pfziyd5nl	cj7q0rqqs0002305por0l7nl1
cj7q0rqqs0002305por0l7nl1	cj7q0ri2y0001855d9yjamf7j
cj7q0mac70000305pfziyd5nl	cj7q0sk5p0003855dn0rxkkcf
cj7q0sk5p0003855dn0rxkkcf	cj7q0sedt0002855dndnrxquv
cj7q0mac70000305pfziyd5nl	cj7rcmbr20000855dc1f44ha3
cj7q0mac70000305pfziyd5nl	cj7rcpv2o0002855dyh5jr56r
cj7rcpv2o0002855dyh5jr56r	cj7rcpru50001855d1ojqrvcn
cj7q0mac70000305pfziyd5nl	cj7rcpkfg0000305psm4y5ewf
cj7q0mac70000305pfziyd5nl	cj7rcqa840003855dcq4st85l
cj7rcqa840003855dcq4st85l	cj7rcqhw60004855dy984uejt
cj7rcpv2o0002855dyh5jr56r	cj7rcqhw60004855dy984uejt
cj7q0mac70000305pfziyd5nl	cj7rcrcix0006855do03obgb6
cj7rcrcix0006855do03obgb6	cj7rcr7ik0005855dywyb72wo
cj7q0mac70000305pfziyd5nl	cj7rcroyb0008855d96ts07z7
cj7rcpkfg0000305psm4y5ewf	cj7rcsj480009855dyl13tlt9
cj7q0mac70000305pfziyd5nl	cj7rctd9c000a855dcuvjbu69
cj7q0mac70000305pfziyd5nl	cj7rcri9z0007855du1k8ij38
cj7q0mac70000305pfziyd5nl	cj7rcs4g90000305pszfimtpz
cj7t2vebr0000305pzgfvkg3s	cj7t2x7p00001305p1j6vkvko
cj7t2vebr0000305pzgfvkg3s	cj7t2x7gt00002a5l04samucs
cj7t2vebr0000305pzgfvkg3s	cj7t2xcb100012a5lvfduj1rd
cj7t2vebr0000305pzgfvkg3s	cj7t2xbzy0002305p5n8s1ryg
cj7t2x7p00001305p1j6vkvko	cj7t2xvk80003305phryrt14l
cj7t2xbzy0002305p5n8s1ryg	cj7t2zxw000022a5llzstufvb
cj7t2xvk80003305phryrt14l	cj7t324xv00032a5l5kjmms8z
cj7t2x7p00001305p1j6vkvko	cj7t32jz100042a5lkx8jnqq9
cj7t2x7p00001305p1j6vkvko	cj7t2zxw000022a5llzstufvb
cj44fe6dt00002y5ouelvvui1	cj7hqm32600003i60do32gwik
cj44fe6dt00002y5ouelvvui1	cj44ff0xm00012y5o3hdw1cgs
cj44fe6dt00002y5ouelvvui1	cj3umm81800012z5kb0afyub3
cj44fe6dt00002y5ouelvvui1	cj80jwc9h00003f5po77buurk
cj4bwqnx400002y5op7fjlbf6	cj4ablk6300052y5od4ezqw64
cj4ee7kvo00061w63sp5y5yqh	cj4eicsq200072z5ne2p3k17v
cj4ee7kvo00061w63sp5y5yqh	cj4eiedgw00023i633lhkj1lr
cj82uwj9y00002y61wve4dp3v	cj82uwn3900012y619yvotgcs
cj82uwj9y00002y61wve4dp3v	cj82uwpm600022y61ol0qmhrm
cj82uwn3900012y619yvotgcs	cj82uxp1c00042y618qkn5ueh
cj82uwj9y00002y61wve4dp3v	cj82uzhjj00052y61nw70e6fc
cj82uwj9y00002y61wve4dp3v	cj82v153f00062y617fwayh9x
cj82uwn3900012y619yvotgcs	cj82ux9hg00032y6198bd1hd9
cj87fj44m0000315p9etud75d	cj87fjdn50001315pwstp2u3t
cj87fj44m0000315p9etud75d	cj87fjoko0005315p1bi49s4x
cj87fj44m0000315p9etud75d	cj87fk4ob0009315p1ppmck76
cj87fj44m0000315p9etud75d	cj87fl2s2000g315pxmwa21si
cj87fj44m0000315p9etud75d	cj87fl4uu000h315p3mi4muhg
cj87fj44m0000315p9etud75d	cj87fkvfu000e315pe2djpz45
cj87fj44m0000315p9etud75d	cj87fkz6d000f315pyz44rd50
cj87fj44m0000315p9etud75d	cj87fklil000c315pdk4vxtzm
cj87fj44m0000315p9etud75d	cj87fka9h000a315p3zp8sl52
cj87fj44m0000315p9etud75d	cj87fkqkg000d315p1q7u255o
cj87fj44m0000315p9etud75d	cj87fkg63000b315phrfxq7q7
cj87fj44m0000315p9etud75d	cj87fwv0m000i315p90qxzx24
cj87fj44m0000315p9etud75d	cj87fjsp30007315p1t2dgz17
cj87fj44m0000315p9etud75d	cj87fjrsf0006315pq50vnksl
cj87fj44m0000315p9etud75d	cj87fjthd0008315p3t9lnyll
cj87fj44m0000315p9etud75d	cj87fjl330004315pmxy9sax4
cj87fj44m0000315p9etud75d	cj87fjk380003315p3amzr770
cj87fj44m0000315p9etud75d	cj87fjgsn0002315pwx3revru
cj8h7gt0p00002c5umobzdytc	cj8h7h8dm00012c5uqrwq25cw
cj8h7gt0p00002c5umobzdytc	cj8h7nfjf00022c5u3grvar0w
cj8h7gt0p00002c5umobzdytc	cj8h7np0t0000315ppnpqpuz4
cj8h7oqr400052c5uezvxh2ed	cj8h7ou9u0002315p6p7p6ncs
cj8h7gt0p00002c5umobzdytc	cj8h7r3r60006315pxjrhx1jt
cj8h7gt0p00002c5umobzdytc	cj8h7r89q0007315pieou2lvq
cj8h7r3r60006315pxjrhx1jt	cj8h7qg670005315pxvqmo3vi
cj8h7r89q0007315pieou2lvq	cj8h7q8y90004315pci3j3zae
cj8h7r3r60006315pxjrhx1jt	cj8h7obim00032c5ujwpzlplt
cj8h7r89q0007315pieou2lvq	cj8h7olm200042c5u88ntk4go
cj8h7gt0p00002c5umobzdytc	cj8h7szs800082c5uieu190js
cj8h7r3r60006315pxjrhx1jt	cj8h7q25y00062c5ud9oxtdzw
cj8h7r3r60006315pxjrhx1jt	cj8h7plfg0003315pgb4pheax
cj8h7r89q0007315pieou2lvq	cj8h7o7fu0001315puvh4mhz0
cj8h7r3r60006315pxjrhx1jt	cj8h7oqr400052c5uezvxh2ed
cj8h7r89q0007315pieou2lvq	cj8h7rfo500072c5uacv19ptf
cj3um5opt00072z5krz63ceih	cj3um0lox00002z5kkoovo1ca
cj441o5it00002z5n3u00e75s	2151
cj48nxw7y00013i5ngyp664gy	870
cj7g7soyj00022y5osxmt32jl	cj44fe6dt00002y5ouelvvui1
cj8h7r89q0007315pieou2lvq	cj8h82er400002r5ugcav6ccj
cj80jywja00003f5pqy4tm3e9	82
cj80jywja00003f5pqy4tm3e9	cj44fe6dt00002y5ouelvvui1
cj8vwjrh20000315pfxsyv1ce	cj8vwjva60001315p8fiegqa1
cj8vwjrh20000315pfxsyv1ce	cj8vwjw040002315pe8at0xtf
cj8vwjrh20000315pfxsyv1ce	cj8vwjx630003315p5u4f3izy
cj8vwjw040002315pe8at0xtf	cj8vwkct90004315pwj2cdmsb
cj8vwjw040002315pe8at0xtf	cj8vwkikt0005315ptvn8zy35
cj8vwjrh20000315pfxsyv1ce	cj8vwxdsi0006315pb1bjmzre
cj8vwxdsi0006315pb1bjmzre	cj8vwxg600007315pjkb36lke
cj8vwjw040002315pe8at0xtf	cj8vwxg600007315pjkb36lke
cj8wrdub600003d65v5wxnngd	cj8wre70800013d65pjd35gk1
cj8wrdub600003d65v5wxnngd	cj8wre9u200023d65f1gi79lo
cj8wrdub600003d65v5wxnngd	cj8wrebyr00033d65dh0oy16j
cj8wrebyr00033d65dh0oy16j	cj8wrekeo00043d65brwbtjfk
cj8wre70800013d65pjd35gk1	cj8wrez8h00053d65ffk0nrxg
cj8wre70800013d65pjd35gk1	cj8wrekeo00043d65brwbtjfk
cj8wre70800013d65pjd35gk1	cj8wri6b200063d65bd7t8m2i
cj8ximo1n00002r5uujivoe4j	cj8ximtqx00022r5ukb4dxt9l
cj8ximo1n00002r5uujivoe4j	cj8ximw8i00032r5uycym97hy
cj8ximo1n00002r5uujivoe4j	cj8xin3vq00002r5uws9ibris
cj8ximo1n00002r5uujivoe4j	cj8xin64z00032r5uothwc5mo
cj8ximo1n00002r5uujivoe4j	cj8xin72w00042r5ufotlakfx
cj8xims9g00012r5utnupg1jo	cj8xingdt00052r5ukfwme1ma
cj8xingdt00052r5ukfwme1ma	cj8xin4n500012r5ulqvtfi0d
cj8xingdt00052r5ukfwme1ma	cj8xin54900022r5uq00ksjb3
cj8xin64z00032r5uothwc5mo	cj8xiodg100062r5u5ogueyuq
cj8xin72w00042r5ufotlakfx	cj8xims9g00012r5utnupg1jo
cj8ximw8i00032r5uycym97hy	cj8xiodg100062r5u5ogueyuq
cj8xivhdk00082r5uwnk39bkx	cj8xivkl600092r5u2sge3raj
cj8xivhdk00082r5uwnk39bkx	cj8xivrbl000b2r5u1mqeh4m5
cj8xivhdk00082r5uwnk39bkx	cj8xixg6x000d2r5uu32kjkla
cj8xixg6x000d2r5uu32kjkla	cj8xivzkt000c2r5ulu83rtdq
cj8xixg6x000d2r5uu32kjkla	cj8xivoca000a2r5umy9f58dg
cj8xj6yu800002r5uw6pvw5x9	cj8xj7o4400012r5ur1osaxkt
cj8xj6yu800002r5uw6pvw5x9	cj8xj7s9s00022r5uihrsuqtu
cj8xj6yu800002r5uw6pvw5x9	cj8xj7uk700032r5ub91qekmo
cj8xj6yu800002r5uw6pvw5x9	cj8xj7zcv00042r5usa47inhp
cj8xj6yu800002r5uw6pvw5x9	cj8xj80yg00052r5utgctaefm
cj8xj6yu800002r5uw6pvw5x9	cj8xj82sl00062r5ujbivr1e6
cj8xj8dvw00072r5umbenjl4a	cj8xj8jhz00082r5umizp9x73
cj8xj7zcv00042r5usa47inhp	cj8xj9j1f00092r5uug3ro32e
cj8xj9j1f00092r5uug3ro32e	cj8xj8dvw00072r5umbenjl4a
cja9iql3l00003j5umcnm6bh1	cja9irqi100013j5utt9hcjc2
cja9iscx200023j5u1i5a2rgn	cja9issv700033j5ursvj84h1
cja9iscx200023j5u1i5a2rgn	cja9isvk400003j5uc6x0o1hz
cja9iscx200023j5u1i5a2rgn	cja9it7yz00003j5utd1y7mf9
cja9iscx200023j5u1i5a2rgn	cja9iufx500043j5uoe2dl32y
cja9iscx200023j5u1i5a2rgn	cja9iuscn00003j5u5iqih7sp
cja9iscx200023j5u1i5a2rgn	cja9iut6j00013j5uspcwfshj
82	cja9iv7gx00053j5udx4gx060
cja9uao4600003u5uupch92me	cja9uau5100013u5u098c1rbi
cja9uao4600003u5uupch92me	cja9ue7at00063u5u26ldqtkh
cj4ac5wpk000b2y5otoo9w19r	cjaf35j6100023b5xtehdh1na
cjaseooe500002y5qa088q3t5	cjaserstq00012y5qra1hb0kx
cjaseqs5e00013b6173kqoszr	cjaseso2f00002z5n5m328ckj
cja9uao4600003u5uupch92me	cja9ugfzf000d3u5ui6izmd3m
cja9uglan000e3u5u1px3ibpc	cja9uctvr00043u5uadwajcjo
cja9uglan000e3u5u1px3ibpc	cja9uegqx00073u5uzkl16bbv
cja9ugfzf000d3u5ui6izmd3m	cja9uft4d000c3u5ucre3hbg1
cja9ugfzf000d3u5ui6izmd3m	cja9uflo8000b3u5urxo3yma8
cj72eh38o00013260smm1ljkw	cjak1nokt0000315s9lnp1urc
cja9uao4600003u5uupch92me	cja9uglan000e3u5u1px3ibpc
cja9uglan000e3u5u1px3ibpc	cja9ubxmj00023u5uajhxqr0n
cja9uglan000e3u5u1px3ibpc	cja9ucpba00033u5ug2xxa9e2
cja9uglan000e3u5u1px3ibpc	cja9uf7y700093u5ub6afyvrg
cja9ugfzf000d3u5ui6izmd3m	cja9ufeqs000a3u5ut7cqdiga
cja9uao4600003u5uupch92me	cja9udyxw00053u5u01ax8r1s
cja9uao4600003u5uupch92me	cja9ueyqk00083u5ugrx3myeb
cj444p6rk00013k5ngce5tpit	cjac91bly00003h5p0ufh7kcr
cj444rdfz000b3k5nxlt0enpc	cjac9322i00013h5pjedid51b
cj4ac5wpk000b2y5otoo9w19r	cjaf352jy00003b5xc0dn1po8
cj4ac5wpk000b2y5otoo9w19r	cjaf38aim00043b5xyy5vugz8
cjaf352jy00003b5xc0dn1po8	cjaf38ye900053b5xnoldgmiw
cjak1omwk0002315s1f6nhl3c	cjak1ov3z0003315s3kouy2d5
cj4ac5wpk000b2y5otoo9w19r	cjak1ov3z0003315s3kouy2d5
cjak1ofiy0001315su6w0ot22	cjak1omwk0002315s1f6nhl3c
cjaserstq00012y5qra1hb0kx	cjaseu0ye00012y5qqi4k9cop
cjasesq2a00002y5q1r3l8rn3	cjasexn5200022y5q6gfety4b
cjasexn5200022y5q6gfety4b	cjasexpi400032y5q395fb6r6
cjaseqs5e00013b6173kqoszr	cjasesq2a00002y5q1r3l8rn3
cjaserstq00012y5qra1hb0kx	cjasew1l600023b61kjn6k7a8
cjaserstq00012y5qra1hb0kx	cjaseso2f00002z5n5m328ckj
cjaseq5id00003b61k86ee0bc	cjaser7o500002z5nqdxjlz41
cjaser7o500002z5nqdxjlz41	cjaseq5id00003b61k86ee0bc
\.


--
-- Data for Name: groupinvite; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY groupinvite (groupid, token) FROM stdin;
54	smjcuiuhjfcntdpo8oat0rmi
55	n01qfjf0rlm84ab0oot4n648
57	ckctg39d9jnaifu2j8u1bvp
37	rq751cpe1fuk7puk39tbtpku
59	1u439jsp3fuo1oq7si38i6l5
64	n9ohesndr45j4907knlv9bqt
68	v39m37fe1thnu0j61idjjq0e
31	ta3jd95i0393br0r9v0e1vbu
72	iue4aaac6aptom0hmb0dc28h
69	3re5mg83n7747gb9o26vqdne
75	d3r7sthgkal53nb0mv13q9a6
77	q1dhkjarhpfr0t10tqveechp
33	2lllopst24imtl1mrs7lf7ih
78	8kjaap4p51mctl1dnacgrmvg
79	kgcl5bju8r1alvlaa62b7tpq
32	os48saoccs2mele9f6elhv5t
49	o0o0srqh50glm35j5g2j2uiv
80	clpnfmm6ttvs8met3t8qaiii
81	f9hol34em33abbdf487165ar
83	vq22l8ug2e3pt7joae067sv2
84	dvdm6m9t5uplprre9hb0d479
86	8htu3ig6dob7sdcu2p3t446d
96	otb6q3tebjnb06kcp8cshr43
99	2buci32vb42cr2j17dfvifqs
102	s46jer24v2j97r3ndgdtu5li
105	fgk0pmp2giria6le5up5i093
113	jj190i8ojrajtg3p4n7cokms
111	fg6it59ll160gvq0af34eud
36	8stk3km13sddf1vtj6n41v95
116	4ao4ackf8otelo4j3j5umd2s
117	5jbke7qa943328hhelp0it5s
130	36v83bqku2j6fp2hm1uhc3n1
137	k18vceoud3eb2s0k2pt9mnoa
138	ugjuhqvhdfpvb9q1jc8t2hm
140	gl3rl3a8a6ghfr08hr140doq
141	tic7dhnere7v6splg98pu4m4
142	uubtpt6et3a5d15eud2pfodb
143	tdt2m7mta3asa5b3oplkhvj5
144	eqr2l2s99u6ev7gn1vd7ekud
145	g7ta7ktdr86bdes8gcncmg9s
146	n4dnedgnaqrpfssj9l45qbq3
148	mbts2mc2vr034s9ecrfbs9rd
149	f3iecsephe7mvmflve70622a
150	n4bk0lp5tfecgio33qlhnhej
156	dkmiopk1dar8adgevhur9dl
157	k60o6a88ar5j1ka59n06uirb
159	hm2v2g9m8a4hbhm8oau2f0k3
162	cdaf9lmctp1bkhpe2j8jasb0
163	bnc6uit0bja1jgposdgsc12q
161	8fmhud6tqvsdsoou9dc0f1c4
165	k4b3niqv3q3j0epmokdgrjj6
166	hs63rc4l327oc67lh77iajk4
167	6up4tl03jr3lham183iji8pm
168	n05c81htqolf91s5tmomej5s
169	pbuuoib6ec2v2gb2pgrd1ge6
170	40qj16h4b3t4dk6lji5f9sm3
171	b53gq7us57djg2ongqivgpfo
172	bgo9n7cts17rgdgjk8ggmjc3
173	5p0qh706ofniguepg1pmnn04
174	h6uj6i2ijsdmiag623qs3ir
34	7pphggrk6oe9jb1vvvgp4vpi
175	1kgajhvhr4iddd082kpsp3q1
176	a2d4nkht9bl8q2j4p9vus6dg
177	4741d70h5k06i0ko1r9jrnne
178	b3gpgcsml22sli7be1b4kh9b
179	d0mcum0fv7cgqbbtpfko42ip
180	r9qkeu6rjr33mtrlpf4ma6vc
181	55r6ns2hnnmcn52dvtf9k47t
182	asnlfp11t26eurr929f8cs0r
183	hjpmi5dkndf7lv2t9tiner1t
184	10f4i3f6d3r1h0r8dlks93ee
185	ggffkv3cg5e8a3m3vrn639gu
186	751jue9ompf1012npujvn809
187	4i4r33f13v7uu5l4rbhr3mso
188	qvqdlqo002hlauun3cpvja3r
189	b10rot6ct15v19d9jo97jo41
190	qnn9cpiqal0f9ql8on9a1dnp
191	i89u3fim1p9mec521mngjrd8
192	j713m2a4keo88i73ipqu90k2
193	91pqm5ump1gdtqg0stcmke0g
194	h60gnhdv3dg8nc5sknpvh33m
195	21st1vs3e75k5tj0f2b5mmbp
196	k9lpmmvttjbl3uri7o3tsohu
197	5uda29hel51qs7mee0mjg5lo
198	puaci73ged6l71slt7srt3il
199	4oqo6p55k1gqllds0fdh64od
200	tli87c59g6pnh3tcksq46848
201	2ff5vo8n1jmd46e3roub2c70
202	paijjlt1m4q5m6jufqgcoo4c
203	gedbi0krpp2s5q1qktdc4sgb
204	pg5kdasmnrak0gh422dd5fj7
206	vmv54e57fav2aadcbj3ia8ib
205	838eadtfg6vhqcnjl7nu6bd3
207	brik8b9kapiivmp7fbgmpelu
208	2pbd1mrogetnfhpub1uvouop
209	865tmglhufpav1k371eon9o5
210	b016lskco14k8tae9r0l1gkn
211	vrfmlv56sn6q7nbkeh42jfe
212	cgiudqg6oehhui0hks55aifq
213	3sprnvghq22a79ebi1riadbv
214	fu40m19ge8mgvqmbj82nqfh
215	aq9m65iou589g9rptgbva4m
216	rp7v74vrl4kd5mt7nnsvn3gk
217	orgbm3e1m9mrf3ciklv7ahgq
218	7k0n8cf716s502tjsmhun36k
219	3nrqr917eed4bcnq33bccrbg
220	vsdt2rsbopi8je6v0u5ffc4n
221	d4ha9mkagv132bom6rj8n03n
222	se5g3uscvo00684mmg8df7ki
223	7merjltk3o6i0a4lgf6k15e8
71	8bfeum5tc5d84502l3b797m1
224	vd3lin590h1ffr0q32p1mjnv
225	gobf5obnrv71bul4rn5efbjr
226	aarsr0jj3doql6a2nl893ifc
227	ghmp62aedg56mlup2mmicegl
228	dpr7rr2crvoi0j7m2d9k1dq2
229	9sd9j8st21mqapkvdt7lbl01
230	ol2cc5gu1uphdjh41ppq13ct
231	a1295hk4jue854p1b89chman
232	p4g95qh4ij864830j7i6bqso
5	d49b6eup8mpdie3sl6jc0tua
233	fr8ffuogfo8ukgspe20v3idp
234	6612608vc0a5iafmv45h1pbn
235	r1bp678u54vf8m43e2spbfcl
236	u2lflokbg8u481iohhnggm43
237	c6rkpu90jgbtr4rhrc5o5t1t
238	tfc14h70vref1cg3chqj8seg
239	frbh8aqv7j8d4pioba1jp5nu
240	jcpbkuer3rnmira49ik2a39o
241	p2jlp8edqhi1g6den1fqjh66
242	2i6fapirur3vvjf007m88buj
243	d0811kc2ro09iic5el25d2ne
244	7rdi78mkfd0b3rq8bjrqmek1
245	2n4mfaa7u1nu69070ihkkqv
246	66hci7aj9h4vh01bdq5ksgpb
247	2t9e7hcmlg7lskju77ldj9vq
248	1q9ho8fgoagvh6hrls399crc
261	pu6184mt3p9rcm0tugiun2dg
249	7okl0uquso4v3ujbie2tli4j
250	1cfgk34luik8kduvgdl31lg1
251	e2hc29mluit0khh2jojj76oj
252	5vteho50f45co6hnis5hg1f9
253	r8edqprtqfsi517e5p8k1ui3
254	uuglegoe18vqbk5prqk0vko9
263	b27lfrh2ejt6f6jkde5lner1
255	b9gqiruqo90esclrnl1f4vnm
256	hclnupqbf76sv6hbl32s3a8c
257	5ukq7be9eaarnjqsf3nbk517
258	eqjq5jld30udj3qashuggage
259	m5igjgki0490anmif9vhac4r
260	9ud5n9oibnitm8rj5826bimq
262	dobo8g9khdmadpqc3j1idkm2
\.


--
-- Data for Name: membership; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY membership (groupid, userid) FROM stdin;
2	1
3	30
4	31
5	32
6	33
7	34
8	35
9	36
10	37
11	38
12	39
13	40
14	41
15	42
16	43
17	44
18	45
19	46
20	47
21	48
22	49
23	50
24	51
25	52
26	53
27	54
28	55
29	56
30	57
31	58
32	58
33	59
34	59
34	58
33	58
35	60
36	61
37	58
37	61
32	61
38	62
39	63
40	64
41	65
42	66
43	67
44	67
45	68
46	69
47	70
48	71
49	71
49	58
50	72
51	73
52	73
53	74
54	61
55	75
56	76
57	77
58	79
59	80
60	81
61	82
62	83
63	84
64	85
65	86
66	87
67	88
68	89
69	90
70	91
71	58
72	92
73	93
74	94
75	58
76	95
77	95
77	58
78	59
79	96
80	59
81	97
80	97
82	98
83	98
84	58
85	99
84	99
86	58
87	100
86	100
88	101
86	101
89	102
86	102
90	103
86	103
91	104
84	104
92	105
84	105
93	106
84	106
94	107
86	107
95	108
86	108
96	61
97	109
96	109
98	110
96	110
96	58
99	111
100	112
101	113
102	111
103	114
99	114
104	115
105	58
106	116
105	116
105	61
107	117
105	117
108	118
105	118
109	119
105	119
110	120
105	120
111	121
105	121
112	122
105	122
113	123
105	123
114	124
105	124
115	125
116	125
117	125
118	126
102	126
119	127
102	127
102	97
120	128
102	128
121	129
102	129
122	130
102	130
123	131
102	131
124	132
102	132
125	133
102	133
126	134
102	134
127	135
128	136
129	137
130	137
131	138
102	138
132	139
102	139
133	140
102	140
134	141
102	141
135	142
102	142
136	143
102	143
137	139
138	139
139	144
140	145
102	145
141	145
142	145
143	145
144	145
145	145
146	145
147	146
148	146
149	58
150	58
151	147
152	148
153	149
149	149
154	150
37	150
155	151
156	151
157	111
158	152
102	152
159	153
102	153
160	154
161	155
162	155
163	155
164	156
36	58
54	158
102	162
165	165
102	166
102	167
166	169
165	58
165	169
102	172
157	173
157	174
167	58
165	175
165	111
165	176
165	177
168	178
169	178
170	178
171	58
171	61
172	179
102	180
173	58
173	181
173	61
173	182
173	165
174	183
175	111
176	111
177	185
178	58
178	61
178	150
176	190
176	192
176	193
176	194
176	195
179	204
180	204
181	204
182	204
183	204
184	204
185	204
186	204
187	204
188	204
189	204
190	204
191	204
192	204
193	204
194	204
195	58
196	58
197	206
198	209
176	210
176	211
199	111
199	215
199	216
199	217
200	111
200	220
200	221
200	222
200	223
201	111
200	224
202	226
202	227
202	228
201	230
203	231
204	58
203	61
205	58
206	61
207	61
208	61
209	58
210	233
210	58
211	61
212	234
213	234
214	234
215	234
216	234
217	235
218	235
217	208
219	235
217	236
219	236
218	236
218	237
220	238
200	239
221	242
221	58
217	245
217	246
222	245
223	58
223	249
200	250
210	251
199	252
200	253
200	254
200	256
224	257
224	258
225	58
226	259
227	260
102	263
200	264
200	265
200	266
200	267
200	268
200	269
200	270
200	271
200	272
200	273
228	274
229	275
229	276
229	277
230	275
200	278
231	58
200	279
165	280
165	281
232	58
233	32
232	283
232	61
234	284
234	58
234	283
234	61
235	285
236	287
165	289
165	290
165	291
237	292
238	58
239	58
165	293
165	294
165	297
165	298
240	111
241	111
176	299
165	301
165	303
242	304
242	305
243	306
244	308
244	309
165	174
245	312
200	314
246	58
246	319
247	58
247	320
248	32
249	306
165	324
250	58
251	326
251	58
251	61
251	150
252	58
165	331
165	332
253	333
254	334
255	334
256	334
257	334
258	334
165	335
165	336
165	338
165	339
102	340
165	341
165	342
165	343
259	334
260	334
261	334
165	344
165	345
262	346
263	58
263	61
263	350
165	351
165	352
\.


--
-- Data for Name: ownership; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY ownership (postid, groupid) FROM stdin;
1366	32
1371	102
781	69
1373	102
1376	102
1378	102
1381	102
794	78
795	78
797	78
800	78
1383	102
1386	102
803	78
805	78
807	78
809	78
1400	102
1402	102
1404	102
1405	102
1408	102
1410	102
861	32
1412	102
1414	102
1416	102
864	32
1418	102
870	32
1420	102
1422	102
873	80
874	80
876	80
879	80
885	32
887	80
1435	102
1438	102
890	32
1445	102
1452	102
1454	102
892	84
894	84
896	84
899	84
1457	102
901	84
904	84
1461	102
907	84
911	86
1463	102
912	86
1465	102
1468	102
389	32
390	32
392	32
395	32
397	32
1470	102
402	32
405	32
914	86
411	32
414	32
416	32
417	32
916	86
422	32
425	32
1474	102
1476	102
1478	102
1481	102
1483	102
1487	102
447	34
919	86
922	86
926	84
930	84
933	84
1490	102
1493	102
1496	102
1502	102
1504	102
1507	102
1509	102
1511	102
1516	102
971	81
973	81
501	32
514	32
975	81
518	32
524	32
526	32
977	81
532	32
535	32
537	32
541	32
543	32
546	32
550	32
555	32
557	32
559	32
1518	102
566	32
979	81
1521	102
576	32
579	32
1524	102
584	32
587	32
590	32
1527	102
599	32
603	32
605	32
608	32
611	32
986	86
988	86
617	32
620	32
626	32
628	32
630	32
1530	102
1534	102
1537	102
1548	102
1554	102
992	81
1557	32
1573	32
1001	96
1004	96
1006	96
1025	96
1584	102
1032	99
1033	99
1035	99
1037	99
686	49
687	49
689	49
692	49
694	49
1041	99
699	50
700	50
1585	102
1043	99
1045	99
1049	99
1587	102
1591	102
1114	32
1120	32
1129	102
1130	102
1131	102
1132	102
1133	102
1134	102
1140	99
1141	99
1143	99
1145	99
1149	99
1151	99
1155	99
1160	99
1166	99
1171	99
1175	99
1177	99
1179	99
1183	99
1185	99
1595	102
1599	102
1194	99
1196	99
1201	99
1203	99
1207	99
1209	99
1217	99
1219	99
1222	99
1603	102
1234	105
1607	102
1611	102
1616	102
1621	102
1626	102
1631	102
1636	102
1643	32
1645	32
1647	32
1649	32
1693	32
1695	102
1698	102
1700	102
1704	102
1706	102
1708	102
1711	102
1794	102
1896	102
1713	102
1716	102
1721	102
1724	102
1729	102
1732	102
1735	102
1738	102
1743	102
1747	102
1750	102
1752	102
1754	102
1756	102
1758	102
1761	102
1768	102
1774	102
1782	102
1790	102
1797	102
1800	102
1810	102
1814	102
1818	102
1822	102
1826	102
1831	102
1833	102
1841	102
1855	102
1860	102
1876	102
1882	102
1884	102
1887	102
1898	102
1908	102
1914	102
1925	102
1945	102
1950	102
1958	102
1838	102
1844	102
1847	102
1870	102
1930	102
1935	102
1940	102
1952	102
1961	102
1966	102
1968	102
1973	102
1978	102
1984	102
1986	102
1994	102
1999	102
2004	102
2006	102
2011	102
2017	102
2022	102
2029	102
2034	102
2039	102
2046	102
2051	102
2056	102
2062	102
2069	102
2075	102
2077	102
2083	102
2088	102
2095	102
2112	32
2114	32
2149	32
2151	32
2160	102
2183	102
2191	102
2200	32
2202	32
2204	32
2206	32
2221	149
2222	149
2224	149
2226	149
2230	149
2232	149
2234	149
2236	149
2238	149
2240	150
2241	150
2274	149
2277	149
2281	149
2285	32
2287	32
2289	32
2294	32
2296	32
2298	32
2303	32
2305	32
2308	32
2310	32
2314	32
2317	32
2319	32
2321	32
2327	32
2329	32
2332	32
2342	32
2357	37
2358	32
2360	32
2363	32
2365	157
2366	157
2368	157
2370	157
2372	157
2391	32
2393	32
2395	32
2398	32
2401	32
cj3px3hfv00062z6046a7p2nk	36
cj3px5mil00002z60ybys832b	36
cj3px5vyq000e315l7dhyyc34	36
cj3px68h8000f315lm0mwdcx0	36
cj3px6ezb00012z60m8l9qkma	32
cj3px70up00003k5k7f02pyi1	102
cj3px718p00002z60x3dqrqcw	32
cj3px7akt00012z60icqhn757	36
cj3px8c1a000g315la2qjxshp	36
cj3px8gkb00072z603behbv62	54
cj3px94r200013k5k3g9c8j7g	99
cj3pxm57i00002z5zfl68o9mf	54
cj3siy5lu00001w5de826nlhg	165
cj3siyxcw00011w5drv0yf004	165
cj3stt8lt00003i60pf5j1qlk	165
cj3stu3sn00013i603e3y4n0d	165
cj3stulfr00023i60naiq4oba	165
cj3stvc9y00033i60eu00ymy1	165
cj3stvwph00043i60vbv4p8pr	165
cj3stwxsj00053i60her1775v	165
cj3stx6oj00063i60o1a4ofjj	165
cj3su0v9h000c3i60ajxotps5	165
cj3sty9gh00073i60cr24n6sr	165
cj3styv1d00083i60iz4375ka	165
cj3stzs4v00093i60nmjofb7t	165
cj3su087a000a3i60qifunrxj	165
cj3su164l000d3i60dt0srq97	165
cj3su4et4000f3i60e3wu4x9w	165
cj3tydvyn00002z5kp6rgvsti	32
cj3ufub6300052z5kbhy9r327	168
cj3su0jye000b3i607noyybc7	165
cj3su3phl000e3i6035mr5l6h	165
cj3su7798000g3i6043yab96g	165
cj3tye0yp00012z5kcyk550n2	32
cj3u3bmyw00003k5krhjpu9gp	157
cj3u3dlmo00013k5kpv3a4ods	157
cj3u3dubc00023k5kd697eedt	157
cj3u3e0m000033k5k2k45w9mz	157
cj3u3eh1j00043k5kd7potwi4	157
cj3u3erx800053k5khoxlm5xy	157
cj3u3m3fx000g3k5kb9sqfnwf	157
cj3u3news000k3k5krbx24qi5	157
cj3u3paju000o3k5km2ic9cfh	157
cj3u5ayfy0005315lmupdjiw4	167
cj3u5azbx0006315lr07id9ln	167
cj3u5l9z20007315lh9t2m1s4	167
cj3u81m7100023w5dxfev62db	165
cj3uaytm500025t5c3p753gcj	165
cj3u3g2t400063k5k842tnha3	157
cj3u3ghoe00073k5k9o3nvir4	157
cj3u3gnxz00083k5koj4pickz	157
cj3uftf9g00022z5kknf0hc3t	168
cj3uftytt00032z5kykhrx4ap	168
cj3u3h2un00093k5k0uhyaywd	157
cj3u3hpfi000a3k5k8cfa5usv	157
cj3u3jbxx000b3k5kcfl8g6u7	157
cj3u3k5fq000c3k5kuerpvpkr	157
cj3u3kbf1000d3k5k10jef8v8	157
cj3u3kd78000e3k5kz4q2h42v	157
cj3u3lq78000f3k5k3fk5itvn	157
cj3u3m4t8000h3k5km7eh1cki	157
cj3u3mci5000i3k5k3i0uziiy	157
cj3u3n6fv000j3k5kni5pss6b	157
cj3u3oa4b000l3k5k67d4otph	157
cj3u3ogzv000m3k5kzuuhql9p	157
cj3u3ot2a000n3k5k8kc33cp6	157
cj3u3su09000p3k5kizmbof8o	157
cj3u58abt0000315laro2hzvl	167
cj3u591v50001315l6c24rbzv	167
cj3u595z90002315lprpavwmr	167
cj3u59eh30003315lqk5u3lyj	167
cj3u59q3a0004315lb4f5atkg	167
cj3u80mvu00003w5dqsgfc6q9	165
cj3u811mw00013w5dv6gddcl8	165
cj3u82fs100033w5dwix8lv0m	165
cj3uasvtf00005t5ceor3ujy9	165
cj3uat9rs00003w5c7zhyzwjp	165
cj3uav0nf00003u5cm2zt00sn	165
cj3uaxqsl00003u5a2kos0h8x	165
cj3uayqsh00015t5c5a50wxw4	165
cj3ufoxpl00002z5kjgb5f1ie	168
cj3ufs32q00012z5ka8up4v5a	168
cj3ufu5vn00042z5k3cghntas	168
cj3ufugkq00062z5kjqh0a737	168
cj3ufv0r000072z5kluu0xzvz	168
cj3ufxet200082z5kcxr7u1u0	168
cj3ug8lgo00092z5kw7rs356v	168
cj3ug91l3000a2z5klm7zzirz	168
cj3ug9g9u000b2z5kleq0bmhy	168
cj3ug9vq9000c2z5kdglisto7	168
cj3ug9zm2000d2z5kpia2c70x	168
cj3ugdizm000e2z5kczg5j8ct	168
cj3ugdnkz000f2z5k6glg8qzk	168
cj3ugdq09000g2z5ktnzev7jf	168
cj3ugdq9m000h2z5krlb1pceq	168
cj3ugdqkn000i2z5klqhs6cx3	168
cj3ugdqq4000j2z5kgaemrdl1	168
cj3uge7b8000k2z5k5id7h1ui	168
cj3ugz9vw000l2z5ku971y273	168
cj3ugza0y000m2z5kgl2ugbef	168
cj3ugza5u000n2z5kb77c4mve	168
cj3ugzaat000o2z5kcgj05psj	168
cj3uh1ky7000p2z5krbssybae	168
cj3uhi48r000q2z5k3r0dzkde	170
cj3uhn8ot000r2z5ka6q38e26	170
cj3uhn8sy000s2z5kjm76wj8a	170
cj3uhn8wr000t2z5kowxzx6bh	170
cj3uho443000u2z5krzazottu	170
cj3uho48v000v2z5k3s0wj9dz	170
cj3uho4cs000w2z5kix4tg6fd	170
cj3uhosu9000x2z5kv744x5l2	170
cj3uhow2h000y2z5k9tox6jzp	170
cj3um0lox00002z5kkoovo1ca	171
cj3um0xn800012z5kr8obr24i	171
cj3um0ylv00022z5kls9wxn0l	171
cj3um330s00002z5kb4hv3p5l	171
cj3um347000012z5kt730m1us	171
cj3um36gt00022z5kbocl79vl	171
cj3um1iqt00032z5k71nn8nvx	171
cj3um1x8a00042z5kuyadylsh	171
cj3um3pts00032z5kcc0fy6ah	171
cj3um3xlw00002z5kckzzczs0	171
cj3um5luh00052z5k3g1mjg9y	171
cj3um4fqb00022z5knz9ieufs	171
cj3um5rnk00082z5kvnb80rvm	171
cj3um51fg00042z5kcqtjinj1	171
cj3um5opt00072z5krz63ceih	171
cj3um46jx00012z5kttwift1z	171
cj3um4wse00032z5klh4b7qtk	171
cj3um65r800092z5kv1skjgcv	171
cj3um5nd100062z5kfvafd25t	171
cj3umgk3j000e2z5kv5hgbkap	171
cj3umfxmw000c2z5kl10uazp7	171
cj3umg46b000d2z5k9f9ken9z	171
cj3ume9om000a2z5kn4akqeo4	171
cj3umelfc000b2z5kwomncj7c	171
cj3vugeid00002z5zqppzn1de	32
cj3vuhkhy00012z5zv4llf5cr	32
cj3vuhp1v00022z5z0wmlyqb2	32
cj3vuii5n00032z5zf8i2awu3	32
cj3vuiok000042z5z77obqoeq	32
cj3vul8p40000315lfa6djk6i	171
cj3vvpeax0000315lxxw93kys	173
cj3vvpf0d00002z5xmpmj6emo	173
cj3vvpt0u0000265evwai3dqi	173
cj3vvqiqq0000265e7wpe1365	173
cj3vvr34r00003t5zyab9dtds	173
cj3vvriki00012z5xrg9waq5j	173
cj3vvuw9i0000265e5rxc30e1	173
cj3vvwfs70001265ewjw0d1no	173
cj3vylsko00002z5z0kqa3alr	171
cj3vymigc00012z5zoo488hjz	171
cj3vymxdu00022z5zu8ecbkeb	171
cj3wrzvdz00013e4ys14vp22i	174
cj3wrzz1x00023e4yt7xx77lf	174
cj3x16hwe00002y5l0dffj95v	84
cj3x3hrmg00003k60qqaazy8a	165
cj3x3ns2b00003k5krzsuquil	165
cj3x3pi4900013k5k9gjeuaiy	165
cj3x3pl0800023k5ka9kypfja	165
cj3x3pmjo00033k5kupwbvj3q	165
cj3x4n3ry00043k5kxem8f6k9	175
cj3x4tq4o00053k5k6alnuxos	176
cj3x4tzq700063k5kwz11thn6	176
cj3x4uzgu00073k5kya5eh586	176
cj3x4wq6600003k5nbi6mfr1d	176
cj3x4xj8j00013k5np9qztf3h	176
cj3x81cl200012y5omnoiwqek	177
cj3x81lhp00022y5ovzapioqf	177
cj3y9lbho0000315obc5atwzl	178
cj3y9ooxp0000315o2urjuxc3	178
cj3y9p9u60001315o8hwy4ik4	178
cj3y9qub800002z63bxi79pm2	178
cj3y9rx1500012z63jv6actr6	178
cj3y9s2qh00022z63r1tvww4d	178
cj3y9sn6100032z63irxo9wrw	178
cj3y9sw0b00042z63ptf0s41k	178
cj3y9t79t00052z63wx43hmds	178
cj3y9tcba00062z63reun8i8b	178
cj3y9thds00072z633owpf7nw	178
cj3y9tnle00082z63ddzhqaxx	178
cj3y9vbjz0002315ozvun1jz0	178
cj3y9vpyt0003315oe0tn5y6n	178
cj3y9vu2z0004315og00g6y0e	178
cj3y9vzut0005315ocykl4l8j	178
cj3y9w4da0006315owv19fzns	178
cj3y9wii90007315of230mjym	178
cj3y9xvbx0008315onn0jdyia	178
cj3ya6mxd00092z63ublkrpst	178
cj3ya6mfg0009315ozjq4e2gf	178
cj3yacf4z0000315o2npoqf8h	178
cj3ye4vql0001315orhzrefo0	178
cj3ye84tw0002315oc98yaidq	178
cj3ya7561000a315ohp0dibzt	178
cj3yaewkz0001315ol5p3derv	178
cj3yai9gx0000315owln7e9b1	178
cj3yakfhz000b2z638525eekm	178
cj3yal6ad000c2z63zedjkeaj	178
cj3yalbyv000d2z63igk0wljm	178
cj3yasg040002315o3uo4t16p	178
cj3ybberl00002z63a62bstta	178
cj3ybchm700012z63rsd494sg	178
cj3ybcmsg00022z63d6q4aml4	178
cj3ybcza600032z63e3yoh309	178
cj3ybdg1i00042z633t79imgv	178
cj3ybdlro00052z631bc9o5u0	178
cj3ybdzfs00062z63c0c6g0og	178
cj3ybek2e00002z63l8xz0ysv	178
cj3ybevem00012z63zu5266io	178
cj3ybf70j00022z63bs5je30r	178
cj3ybfbgw00032z634h39zk8g	178
cj3ycqs6q0003315o0shc3va8	178
cj3ycrcbf0004315o44un4a20	178
cj3ycszf00005315ov1d6kvdu	178
cj3yct9ef0006315o1waj1vqe	178
cj3yd26sa0007315ofrkz826z	178
cj3ydanh900032d5m33k0cc6j	178
cj3yddv7q00042z63qpcykbx3	178
cj3ydfq4h00052z637agduxgr	178
cj3ye4gcs0000315ouqy8i3p9	178
cj3yjl9ih0000315okm62gk1v	178
cj3yjlioh00002z63si2p5gj2	178
cj3yjlp040001315otrho96ex	178
cj3yjo2qi0002315od9o38swp	178
cj3yjoenn00012z63k96z76yj	178
cj3vvprwg00003i5x4lpggym4	165
cj3vvw8uu00053i5x3djwsp3s	165
cj3vvqv1q00013i5x1pv6w796	165
cj3vvr8x900023i5x9jcfd9zr	165
cj3vvvr2f00043i5xdcdfxyuz	165
cj3vvrx5g00033i5xt3pobe8v	165
cj3ykgogm0000315orgivkj9k	178
cj3ykgwdq0000315ongzowvwb	178
cj3ykh4g40000315o71fzvrac	178
cj3ykygne0000315o8olggsl3	178
cj3ykypss0001315ozrw45utf	178
cj3ykyqk300002z63y0ytul5d	178
cj3ykyzke0002315oxxufn80x	178
cj3yl0jwa0003315o2kmz04a4	178
cj3yl0swu00012z634bm7xq5e	178
cj3yl109s0004315ok3xv68fv	178
cj3yl1ejf00022z63gg8tp7fe	178
cj3yldjo400003f56c28j276l	176
cj3ylez4q00013f56rtnf9xvj	176
cj3yoh6dn0005315ovicjvk5r	178
cj3yoi31i0006315oiavnc2c0	178
cj3yoicsf0007315o9sl0vb7c	178
cj3yoifv60008315ocyin1glw	178
cj3yoij0b0009315opzcm7yig	178
cj3yoiym8000a315ord9pt207	178
cj3yoiyp900002b5mnr97ka18	178
cj3yojg42000b315oroqzwrjp	178
cj3yojrmk000c315op7d3rtv2	178
cj3yojwwv000d315owvzisn94	178
cj3yokj1900012b5mtumnex5k	178
cj3yokolq000e315ohl6uh0rr	178
cj3yol1n800022b5mef1s33jc	178
cj3yolsjb00022z63rqm18xfx	178
cj3yom01700032z63t14a7w56	178
cj3yonipk00042z639jbpw9oe	178
cj3yoldvm00012z63hxrqt2yf	178
cj3yol4zl00002z63xs0bgvp3	178
cj3yooieh0000315o5ymc17d2	178
cj3yop6fa0001315o5avvuwew	178
cj3yoqqv00002315ojwn1kxjv	178
cj3yoqxi80003315o2e4lz2gu	178
cj3you64z0004315o72udoxut	178
cj3youfci0005315owjqlj8o4	178
cj3youmik0006315oxeo92spt	178
cj3youxlg0007315o4ygsgs1w	178
cj3yov4bf00002b5mhrhai798	178
cj3yovcfs00012b5md954j8vc	178
cj3yow8b00008315owqxdjqun	178
cj3yowamx0009315o3brhe0vn	178
cj3youmtx00022z63655c9sj0	178
cj3yoqz4p00032z63hpz5mc4m	178
cj3yoq2wi00012z6313klqyju	178
cj3yow0l200032z631ialegtb	178
cj3yoqnf400022z636amvyizf	178
cj3yotvxc00002z63dowma3fo	178
cj3yowji5000a315ofme37z47	178
cj3yowszj000b315ods4mnj8l	178
cj3yoxbdj000c315oag9a73uk	178
cj3yoxdqc000d315o8jtmwqlf	178
cj3yoxkdp00022b5mi9cdiws3	178
cj3yoxnbb00032b5m6xn80z12	178
cj3yoy8wt00042b5mslzlnn2q	178
cj3yoyr50000e315oxdelpoqy	178
cj3yoyx18000f315ouv2mhyuh	178
cj3yoz9zo00002z63gpnsd9wv	178
cj3yozfn200012z63seew176g	178
cj3yp1raw00022z638zvcjl7b	178
cj3yp5vss00032z63hhqb05b4	178
cj3yp7q3q000g315ouc4b01r6	178
cj3yp7vmw000h315ovcw5da4z	178
cj3yp854k000i315ocuypbzif	178
cj3yp8g4n000j315o7c6v9afw	178
cj3yp9s8200042z63hddqhl4g	178
cj3yp9zc500052z63cwzk52qe	178
cj3ypa13600062z638ve8dffv	178
cj3ypaft000072z63emx3c5rr	178
cj3ypbbnx00082z63sbjltsby	178
cj3ypblna00092z63hez6ge89	178
cj3ypd44q000k315okzr0ty1j	178
cj3ypd99y000l315ohkmnw8pq	178
cj3ypdjgp000m315ozyqyty8p	178
cj3ypdo95000n315onwovsrad	178
cj3ypduwk000o315o223istw5	178
cj3ypdzl8000p315o63tu2rjh	178
cj3ype2wk000q315ouwxnli4v	178
cj3ype5sf000r315oi6jplfjd	178
cj3ypecz6000s315o5nf6k5ow	178
cj3ypek8f00052b5ml43detqk	178
cj3ypemy200062b5m1xez9fvb	178
cj3ypetn600072b5mzmimh6yu	178
cj3ypexv100082b5m17t5qwq7	178
cj3z0pgqw0000315orq2w1wcq	75
cj3z0pl1b0001315orbt5374l	75
cj3z0pnte0002315ox1zoq7k0	75
cj3z0pq3l0003315ojtlcgoxo	75
cj3z17ige00012y5ohic1gref	178
cj3z1aign00022y5oqgwmizia	178
cj3z1avwp00032y5oib91irwn	178
cj3z1badj00042y5o7x4tffag	178
cj3z1bo4300052y5ojmmpa60e	178
cj3z1c2w900062y5onte6yzwg	178
cj3z1d5e700072y5o1vv10juw	178
cj3z1dmr100082y5oayyzfvl7	178
cj3z1e0vm00092y5obuks7pl0	178
cj3zixwju00023k5ncv1j2t4w	176
cj3zj0br900003o51q7sifdoc	176
cj41v85ib00002y5owutkkqrp	179
cj41vdzb5000e2y5oss94fu4a	188
cj41v9w6r00012y5oi8ycbcvf	180
cj41vaiyo00022y5o4p60kptw	181
cj41vawu000032y5owdl807bd	182
cj41vb7x900042y5okrpymy6s	183
cj41vbuqp00052y5orxbm8bk8	183
cj41vc24w00062y5o9110r7tp	183
cj41vcek600072y5o42mphsii	183
cj41vczrr00082y5olai84qsl	184
cj41vd53i00092y5o8ksyqfgf	184
cj41vd9o2000a2y5ojwhpu1m6	185
cj41vdeck000b2y5o7kx42nm6	186
cj41vdkwh000c2y5o6xdnah3v	187
cj41vdo27000d2y5ovtekodk8	188
cj41ve5fx000f2y5olb54xt0w	189
cj41vefuw000g2y5ovydadho1	190
cj41vek5u000h2y5ogj59gqnk	191
cj41vlqua00002y5old1dy71o	193
cj41vxf6s0001315o3ivubg1v	195
cj41xv4iy0000315odxh85ov0	196
cj41xvdl10004315oyop8xs77	196
cj41xvdvm0005315okbqqxedn	196
cj41xve5g0006315ocmvbb5l6	196
cj41zl75f00032y5oq1izxu61	185
cj41zl9gn00062y5o5htl4934	185
cj41zlacz00072y5oyq4tk5pd	185
cj420qd0p00002y5o8y2esg6m	197
cj41verrc000i2y5obp8nkpbt	191
cj41vf5bg000j2y5o4rkqlfwy	191
cj41vlf39000n2y5orhxewfbw	192
cj41vfj1z000k2y5o0ctda7vr	191
cj41vg0ao000m2y5oq2cx44hy	191
cj41vlufn00002y5o1rmvcbk9	193
cj41voc2200012y5oq4k0dbpc	194
cj41w0zwg0000315oy8uph3b2	196
cj41y9u12000b315oxw1beii6	196
cj41xv72q0001315ov746a0i8	196
cj41xv8g90002315oo7hjtvlo	196
cj41xvalb0003315oy5n5zzbe	196
cj41xvef50007315o0nmrbl2v	196
cj41xvf6a0008315obfgpb8hw	196
cj41xw1b20009315o3lgzpied	196
cj41y9sef000a315odcjnpden	196
cj41zl3h700002y5o51lf2q9f	185
cj41zl67800022y5oa9j159ng	185
cj41zl83p00042y5ouyt889xe	185
cj41zlbcw00082y5oz3tatntp	185
cj41zlc5u00092y5omctv6q5e	185
cj41zl56y00012y5o4ms1txo3	185
cj41zl8va00052y5or0rldcl9	185
cj4205ymx00022y5otctmpcqm	194
cj434jmx900002y5ocxsusnsl	198
cj434kidc00012y5o3krnshwc	198
cj4444owi00003k5nj5lii8ok	157
cj444p6rk00013k5ngce5tpit	157
cj444psd400023k5nmdtplsu7	157
cj444q1a000033k5nitov4j9a	157
cj444q3e700043k5nx0u2euzh	157
cj444q98v00053k5nq7uf7jt2	157
cj444qhkv00063k5nfuwjdt3p	157
cj444qqh200073k5nb95ehjay	157
cj444qvlb00083k5nqfre03bu	157
cj444qy8f00093k5n6enn5ugm	157
cj444r8ji000a3k5n3dme7yv2	157
cj444rdfz000b3k5nxlt0enpc	157
cj444rjgu000c3k5nexybg1n0	157
cj444rn1i000d3k5nyu0zzqjn	157
cj444rrda000e3k5ng1iiwdr6	157
cj444tdvg000f3k5n30jthq8j	157
cj444tncj000g3k5nr7hn17h0	157
cj444trnv000h3k5nwsnyeiyy	157
cj444u6ub000i3k5nubryy020	157
cj444ud1f000j3k5n27rhhohz	157
cj444ui2a000k3k5nieuhtfd1	157
cj444us42000l3k5n5f3mmgr3	157
cj444utdf000m3k5ne21r895r	157
cj444uuki000n3k5ntbecxjqv	157
cj444uxia000o3k5n9crfym40	157
cj444uywh000p3k5nk4gyrkt7	157
cj444v7nu000q3k5n9rr2qnga	157
cj444vgmp000r3k5nldgks7bg	157
cj444vsf5000s3k5nwokrn82u	157
cj444wbjt000t3k5n7gphimvb	157
cj4455vbw000u3k5n5v3vauzz	157
cj44567wz000v3k5nhugxcfcu	157
cj4457w9y000w3k5n1ix4vo5x	157
cj445l8ox000z3k5nslllxgzw	157
cj445li6p00103k5nij3bqzw2	157
cj445s3uf00123k5ns9wcrzll	157
cj445s79q00133k5nbhsvrjgy	157
cj445sa3a00143k5nou2f9thn	157
cj445scrq00153k5nilefkdjl	157
cj445swjx00163k5nlefnvz11	157
cj445thys00173k5nzo3r1aug	157
cj445tl4c00183k5n0dzbfvpg	157
cj445u3mc00193k5n9nqntirc	157
cj445uqoj001a3k5n3xq6e4gn	157
cj445v8ga001d3k5n7ve4v6qb	157
cj445w66z001g3k5nm4b9k561	157
cj445y0u0001i3k5nw1yw180g	157
cj445uyj7001b3k5noz4962fc	157
cj445v3rv001c3k5n523v8y7s	157
cj445vjcq001e3k5nho1bx9fm	157
cj445w3rv001f3k5nuje3ftuy	157
cj445xqy0001h3k5nx3jcyzgf	157
cj445yps0001j3k5nbejesrc9	157
cj445yrsf001k3k5no6wtj9iq	157
cj445z0dk001l3k5npi09pyt1	157
cj445ziy6001m3k5n4sueohrf	157
cj445zsja001n3k5ndr6ic741	157
cj44610ro001o3k5nrq5puuz2	157
cj44619m3001p3k5novf91zml	157
cj4461kbo001q3k5nv54lmgf0	157
cj4461tsa001r3k5nmedgl13e	157
cj44654mu001s3k5nl1o06jy6	157
cj4465fbx001t3k5nlw25s3ji	157
cj446sg32001u3k5n27hq2cze	157
cj446u37y001x3k5nk9ssylw2	157
cj446xymg00213k5nmuwu82jj	157
cj446yezs00233k5nwowgv617	157
cj45jl2wa00003i6393hvbov4	199
cj45jmhif00043k5nrrgo2enm	199
cj45jmkbj00053k5n7kmahkex	199
cj45juvec000l3k5n1gbgsmbl	199
cj45jv3l8000m3k5n2nhcxulm	199
cj45jw3bf000o3k5nwf0f5lqf	199
cj45jwwcu000q3k5nt70t596f	199
cj45jy0qt000t3k5nw8o2psdo	199
cj45jzkn8000v3k5ns7jm09aw	199
cj45k0xu1000w3k5n2txrqf36	199
cj45k12bl000x3k5njv0rnr37	199
cj45k31a600113k5nmugr8y08	199
cj45kk5ep00133k5nuch51jaq	199
cj45kl9ry00163k5n4zp57v54	199
cj45kvvtj00173k5nzeitlpa0	199
cj446ttam001v3k5ng0dr045i	157
cj446u0ty001w3k5n48m8nxvx	157
cj446ub1x001y3k5nixdgz96u	157
cj446vene001z3k5npsz4adgx	157
cj446vneq00203k5n5lnad1mt	157
cj446y9jk00223k5nlklycx8j	157
cj44763pu00243k5n0le6xd52	157
cj45jh3ms00003k5nl42coy3j	199
cj45jj2gn00013k5no8feremv	199
cj45jj5tj00023k5nroug61y6	199
cj45jk2my00033k5nmepmbp57	199
cj45jl6yw00013i63jeg9e0cl	199
cj45jl9ig00023i63mdll3jx5	199
cj45jlhr500033i63leh02f9y	199
cj45jmmwm00063k5n7io37r5p	199
cj45jn2lr00073k5ntkdaulp3	199
cj45jn59300083k5n99c9kwaq	199
cj45jnyk600093k5n8vlblchw	199
cj45jodvi000a3k5nlp8ni9ag	199
cj45jufn8000k3k5n7t4cad31	199
cj45jwdez000p3k5np6yr9s0y	199
cj45joop2000b3k5n91dnvkih	199
cj45jpnwt000c3k5nkkox610s	199
cj45jptg4000d3k5npvkg3v17	199
cj45jq2nw000e3k5nlcgwiciy	199
cj45jqyr8000f3k5nbhdimcx0	199
cj45jr3it000g3k5nktwpuocm	199
cj45jrcos000h3k5nstsf1fao	199
cj45jsu84000i3k5nod8jrrn9	199
cj45ju1mt000j3k5napqxzzkc	199
cj45jvik4000n3k5n4p05cntm	199
cj45jxuh2000r3k5nu498hsv0	199
cj45jxyf1000s3k5nslpp1vuq	199
cj45jyccl000u3k5nrapa38bx	199
cj45k165d000y3k5norkzmif9	199
cj45k1afd000z3k5nsr49coi6	199
cj45k2r3b00103k5nslbc4rdd	199
cj45k4m0v00043i639dodyjr3	199
cj45kfq6i00123k5nt61ucm1x	199
cj45kka2p00143k5n4so53zbm	199
cj45kkdpc00153k5nh87eumev	199
cj45kw5zo00183k5ncg0fpl2a	199
cj45kwcdf00193k5ndh0nqocg	199
cj45l5gyz001a3k5n1i5k8irp	199
cj45l5s42001b3k5nhblmvz9m	199
cj45zmr0600003k6219ggfj74	200
cj45zoqtc00003k60c7o2eg6e	200
cj46l2ydl00013k5nurnw06fw	200
cj46l329s00023k5n482sl6tf	200
cj46l3s4m00033k5nv5p9xoes	200
cj46lbm0o00043k5nyfouw3o7	200
cj46lceun00053k5ngaljyobv	200
cj46ldrc600063k5nvspxzdjg	200
cj46lfi4z00083k5ni0rszo3e	200
cj46llkv600093k5nx3ohajpf	200
cj46llo0x000a3k5ncnbsk3tl	200
cj46mgi90000b3k5ng1oma8h5	200
cj46mgl04000c3k5nmx058aua	200
cj46mgtyz000d3k5nd2avp63x	200
cj46mj5ob000e3k5n4pucde6z	200
cj46ovq81000f3k5n4j1ioyi6	200
cj46owu3x000g3k5nz0k9sive	200
cj46oxkr4000h3k5nvjdztcnb	200
cj46oxq14000i3k5ndf02k4ba	200
cj46peqle000j3k5nu68kiz8s	200
cj46pzgh90000265kdbn82vus	200
cj46q469l0000265k8b24sua0	200
cj470td0900003k5cjq7guk6k	200
cj479de3z0000295mfi2kaq3s	200
cj47c283z00003k63wmi2t9tj	201
cj47c2b8100013k63kzaunblb	201
cj47c2fw900023k63f6o2yxi9	201
cj47c2j7t00033k632swx2y9p	201
cj47c2mgx00043k6331i2es0z	201
cj47c348p00053k63e8kuvszc	201
cj47c3m4800063k63uu7wct94	201
cj47c3o1b00073k63o15ewlq5	201
cj47c3v0f00083k63bpvdxxe1	201
cj47c433z00093k63ifq9bz05	201
cj47c47jb000a3k635b406i1z	201
cj47c4efq000b3k63ongr1v3a	201
cj47c4k1q000c3k6366ndez2r	201
cj47c4tc7000d3k63hhf4hytp	201
cj47c50qu000e3k63ornob9tc	201
cj47c55sd000f3k63nn5dtnv3	201
cj47c61at000g3k633u7h20t6	201
cj47c6cn0000h3k63ykkx9bwu	201
cj47c73ic000i3k63oapfvjsi	201
cj47c76nf000j3k63qxppgdo9	201
cj47c8415000k3k6372d4oo9p	201
cj47c885l000l3k63kez4j537	201
cj47c8q94000m3k63pt64iwv7	201
cj47c8vww000n3k63fdz0asz3	201
cj47c948g000o3k6373fxlrpv	201
cj47c96lk000p3k633v4i0hda	201
cj47c98s8000q3k63j3zxe72b	201
cj47c9fbb000r3k63q6vid8wh	201
cj47c9ish000s3k63dfykh369	201
cj47c9kdj000t3k63he6i4ktr	201
cj47c9lpj000u3k6384zt7ns3	201
cj47c9ozi000v3k633mfcwsxy	201
cj47can6e000w3k639ou0lupn	201
cj47cc3h8000x3k63u0t5ma7x	201
cj47ccnub000y3k63ax2658bh	201
cj47ccsde000z3k6358empyis	201
cj47ccxkq00103k63t1gozz89	201
cj47cdc6200113k636jn8o3ve	201
cj47cenzk00123k63y6t51y2c	201
cj47cfa3z00133k63o13d5oyr	201
cj47cfez300143k63u0rkef7z	201
cj47cfo1b00153k63zi748r2o	201
cj47cfv2600163k63nzj0rwqo	201
cj47cg3g600173k63b5trw0zq	201
cj47cgc9h00183k63aiajm17x	201
cj47chbbw00193k63gwomxa5u	201
cj47chhgs001a3k63adj2u4qs	201
cj47chk7g001b3k63xisccdgi	201
cj47cho4z001c3k636ts62nnp	201
cj47ci6jn001d3k63ree3bg4z	201
cj47cidw3001e3k63v2wrm15m	201
cj47ciyrm001f3k63kg5m6unm	201
cj47d9432001g3k63h5hfj7av	201
cj47d9dhh001h3k63rgehdu8c	201
cj47da92k001i3k63o8688t99	201
cj47dbsoq001j3k639zwgk6h2	201
cj47dbw1e001k3k63hq3w0jre	201
cj47dbzip001l3k63qnz9owtu	201
cj47dc63t001m3k63twfrv0gl	201
cj47dd46o001n3k63lc426lid	201
cj47ddqjj001o3k63w2qk3tbo	201
cj47djvpy001p3k63yonrq5dr	201
cj47dmaob001q3k63jj4sbpwy	201
cj47iimel00003i63ghtgg5ex	202
cj47ij5tj00013i63nnt6bc5c	202
cj47ijjms00023i63639a7mrv	202
cj47ijn5a00033i631mlon7am	202
cj47ijpwe00043i63o3cvn96f	202
cj47ijs1500053i63n820dep5	202
cj47ijuy700063i635qa4ipyq	202
cj47ijxdq00073i6331h3zdb4	202
cj47ik07100083i63b4xaid1t	202
cj47in19100093i63d1o4lvih	202
cj47inptk000a3i63hdf26uzm	202
cj47inxc8000b3i63himcjtf2	202
cj47iob3h000c3i6384a4rbft	202
cj47ipe6j000d3i63v62vjplp	202
cj47iph7f000e3i63wciaxikd	202
cj47ipljo000f3i63tcyr5r83	202
cj47ips5e000g3i63qt4snml4	202
cj47j09lg00001w5iu6uefyww	202
cj47j1njk000h3i63cvvwjx97	202
cj47j1kd600011w5if4cvhdr7	202
cj47j1otj000i3i63btbku1db	202
cj47j1prv00021w5iylfp1pbm	202
cj47j1q78000j3i636wgr73fq	202
cj47j22q5000k3i63o5aioed5	202
cj48nwrvl00003i5nrd4qsivr	203
cj48nx7uo00013i5n5lx9jqt0	203
cj48nxbuh00023i5n72zfdg6y	203
cj48nxekv0000315oev0k3hbp	204
cj48sxo6f0000315ouflqxjj1	209
cj48nxhrn00033i5n3uane530	203
cj48nxkxf00043i5nuxhn9lqw	203
cj48nxtoz00003i5n11fju63i	203
cj48nxw7y00013i5ngyp664gy	203
cj48nxyxp00023i5n432opq2a	203
cj48o50eb00002y5oi2f4g8j1	203
cj48oen2i00012y5oahvwm7fw	203
cj48p4dmu00022y5op6afdv7l	206
cj48p23we0001315omsdgdfp3	205
cj48p4k5300032y5oq4ceb833	206
cj48ptfz700002y5ohwafz8uw	207
cj48pua5i00012y5op7yds2py	208
cj48swvum0000315oxnvy2c0m	209
cj48sxx600001315o8zfmph7a	210
cj48syh9b0000315oajolbu3v	210
cj48syjiy0001315oz6wxirqc	210
cj48sz3610002315orwzn0t1w	210
cj48t0f270003315o3zihfb5m	210
cj48t0hvw0004315o39ma1io7	210
cj48t0s9e0005315obantxrlg	210
cj48t1mnu0006315ogv0lr5mt	210
cj48t37ql0007315ocewdqdya	210
cj48t415c0000315ov3jeedu1	210
cj48t6q1h0000315oi229beqz	210
cj48t77ax0001315o5jnz2ni0	210
cj48t7m210002315ov3k4ha15	210
cj48t9fzr0000315ox5cg9ehd	210
cj48t9mue0001315okgwn8hb4	210
cj48t9w2d0002315ol435k6m3	210
cj48ta8df0001315og6fcet3n	210
cj48tfcj000003u69kc4b5ij5	210
cj48tfv4u0002315o78t7dqnl	210
cj48tgi3300013u69k3bkpmdh	210
cj48th13a0003315ot679llkd	210
cj48tnnqh0003315oi3287olo	210
cj48trdqh0004315o1xsgqre7	210
cj48ts9sq0008315og1946hxu	210
cj48uswof0005315o4ot74lhp	210
cj48ut8wa0006315ocmug9c2z	210
cj48uujdf0009315owc6rcel5	210
cj48uunls000a315ogoux3h3e	210
cj48uuqgt000b315ov2m8w7uw	210
cj48uuxbp000c315oc9ayk7gi	210
cj48uv5hu000d315owboa5szm	210
cj48uveuj000e315ojp1dmhxk	210
cj48uvppf000f315ouu836vb1	210
cj48uw1p2000g315oz9wrk6oy	210
cj48uyg4d000h315obt74zhh0	210
cj48uzbhy000i315okiye7gfy	210
cj48v0z7y000j315oxgmiq6ny	210
cj48v1kpu000k315ogrdvj2nk	210
cj48v25wv000l315orr5qmu7p	210
cj48v5gt7000m315ocs0yrow6	210
cj48v988i000n315oysixrejl	210
cj48v9eia000o315oiae2jmtm	210
cj48vb89k000p315ot5q795mm	210
cj48vbkr3000q315ownpo85nx	210
cj48vcnaj000r315ojtqp9l21	210
cj48vqpfr000s315oy3i86xg2	210
cj48vqvqs000t315o7ofvrcc1	210
cj491077o00002y5ooyycxkmi	211
cj4956lke00002y5os69tes27	212
cj4956qqd00012y5ols4rys5x	213
cj4956sw200022y5ow7fpdq9c	214
cj4956x9o00002y5oscli1ywh	215
cj49573ld00002y5os35mo750	216
cj4958r1w00012y5o672cy9c3	217
cj49590si00022y5og4h0o6e4	218
cj4959d9r00032y5o7zivoxrh	217
cj495c75o00042y5oaazmr04m	219
cj495es7500052y5otmf6wcqp	219
cj495evnd00001w5we8vrl4wz	219
cj495gcnx00002y5oclpkb4qc	218
cj495i3xd00001w5w5ith8b0e	218
cj495il3o00011w5wbh3zvrvd	218
cj49ebyc700003r54enioonkz	220
cj4a1bnvh00001w6599vwaht5	221
cj4a1e1gd00011w659y8xl19c	221
cj4a1el2o00021w65crdjv90c	221
cj4a1gaqf00031w65vos3r8eg	221
cj4a1hb5100041w65fedt6mwd	221
cj4a1ko6s00001w63c4iuis0w	221
cj4a1n80800001w63ow00fpt5	221
cj4b5wr4f00003k63yrk7i7nk	201
cj4b5x1m600013k63soae0o2q	201
cj4b5x57y00023k63y3v0oawn	201
cj4b8bw6y00002y5oweda5vrj	222
cj4b8c7tj00012y5okof2e5wa	222
cj4ee6by200001w63lxrk03e0	223
cj4ee6rhf00011w63kwxbljno	223
cj4ee6v6w00021w63mp2ls0sh	223
cj4ee6xil00031w63ppmat88r	223
cj4ee6zol00041w63mgbb2iuo	223
cj4ee735t00051w63o4zv5r78	223
cj4ee7kvo00061w63sp5y5yqh	223
cj4ei5plm00002z5nup8jrxud	223
cj4ei5sip00012z5nlgj5cogv	223
cj4ei5wpx00022z5nwk3cv0ed	223
cj4ei5zxh00032z5njbquweq8	223
cj4ei662t00042z5n6pnhkhg3	223
cj4ei6ab600052z5neiltxzn5	223
cj4eicsq200072z5ne2p3k17v	223
cj4eid7nd00003i63e3xjxp7e	223
cj4eie34x00013i63slr8raom	223
cj4eiedgw00023i633lhkj1lr	223
cj4eiex0h00082z5ni9a9wrco	223
cj4eifbon00033i637y9ste9b	223
cj4eig81a00092z5n09h9d0y1	223
cj4eih2xq00043i63ku92v4x3	223
cj4eihnad00053i63l7j82r1m	223
cj4eihyml00063i63v3ui547w	223
cj4eijgd9000a2z5n1tbpu5hj	223
cj4eijx5e000b2z5nkh6gsiib	223
cj4eiki1v000c2z5nxn7ahzmv	223
cj4ejvck500073i6300js8sjq	223
cj4fovg8r00002366dzhwwyd2	200
cj4ftbz270000295pk5f3n4hw	200
cj4qssxa000003c64szhnu3zr	224
cj4qst5yi00013c64rt7lrhf3	224
cj4qstdup00023c64yta1gkx1	224
cj4qstiss00033c64pae8def0	224
cj4qsu4h900043c644bvtu97g	224
cj4qsudao00053c64rfswbhq5	224
cj4qt8ipk00003c64e7791iqh	224
cj4qtbrwe00013c64sdy7ma2n	224
cj4qtbz0y00023c64nua2sdmt	224
cj4ymfzeu0000315ozejbabjt	225
cj4ymg2a40001315o2ud3zckr	225
cj4ymg4fj0002315o6f3xdgll	225
cj4ymh5cl0003315ocn5yk4x9	225
cj4ymhh4a0004315onbm9xmwk	225
cj4ymhhw60005315ok1ckizf2	225
cj4ymhiqn0006315o1cl8ki6l	225
cj4ymhkw80007315o1loxkein	225
cj4ymhlt20008315onhbk9oli	225
cj4ymjm3o0009315oqrc5w7yn	225
cj4ymkxoo000a315oljvg9ix1	225
cj4yml8ps000b315o9lvnskhm	225
cj51emfpk00003s4rn3f9mom1	227
cj51emoyx00013s4rmpshe0lw	227
cj51en4tz00023s4r6osriocd	227
cj51enlcs00033s4rpjvme9jv	227
cj51enupi00043s4r0fbddgin	227
cj51er3sj00053s4r90uvgql0	227
cj5fqira800003q681spcpcgc	228
cj5fqj3j000013q68oqy0dkui	228
cj5fqk1x500023q68zb7uqnx4	228
cj5fqqi8f00003i62yg5t2vp4	229
cj5fqv70k00012z5rek9nme1r	229
cj5fqvwf100043i62ve0x7mt5	229
cj5fqzocs00022z5rfidhsg0k	229
cj5hcr16s0004315oek3phqhd	231
cj5hcswg40005315ovvq5720l	231
cj5fqrybk00003i54faiiuhmf	229
cj5fqs0ti00013i5401q15yqk	229
cj5fqu5nj00023i62ao5invw8	229
cj5fquirz00033i62d7pcxgwb	229
cj5fqtmos00013i6276f7z76c	230
cj5fqu8g100002z5ra2tvdm8z	229
cj5fqz8x600003i62ni5a6uff	229
cj5hcq13m0000315ohkm0bvfz	231
cj5hcq7o40001315ozs3k66uw	231
cj5hcq9rk0002315oo6nlrw04	231
cj5hcqfqq0003315oz24sfenp	231
cj5khaxje0000315oj5r7ywe6	232
cj5kigrvr0001315o7fr1lpq1	232
cj5l70d4a00002y5qye5fpk31	233
cj5ln04uy0000315nzf1cn0ic	232
cj5ln0him0000315oewok5r55	232
cj5ln0q9p0001315nnd99jjet	232
cj5ln0us10001315o8rhrljy7	232
cj5ln3fm10002315ng00j9aa8	232
cj5ln3ov00003315nsmq93zoc	232
cj5ln421z0004315n5oi8j2cf	232
cj5ln4l2g0005315n2w7nb9pm	232
cj5ln4mvh0006315ndoyrvbi4	232
cj5ln4s200007315niccd3stx	232
cj5ln4vep0008315n23c99kj1	232
cj5ln4vy40009315nfah6eju4	232
cj5ln4wjj000a315nfn3uwlzv	232
cj5ln4xaq000b315n9wlt2h3r	232
cj5ln4xwt000c315n18hbnpzc	232
cj5ln4zgm000d315njnnnf7rs	232
cj5ln502p000e315nxu368613	232
cj5ln50ok000f315nyw8bngxi	232
cj5ln5pck000g315nk8rcbc91	232
cj5ln737z000h315nglqx4bhs	232
cj5ln75ol000i315nldtoohgw	232
cj5ln79a5000j315nn59exi8b	232
cj5lpsgvk00002c5he0mgppjq	234
cj5lpv1yv0002315o5qpewjf7	234
cj5lpvcyj00012c5hhct4v34f	234
cj5lpwcvm0000315ogbedui71	234
cj5lpwtba0001315oknkkj4mn	234
cj5lpxiqw00022c5huhke1kdc	234
cj5lpxla40002315oijhq4pln	234
cj5lpxo5w00032c5h6z172gsa	234
cj5lpycee0000315oo2qoxcsv	234
cj5lpyzmh0001315o2pzpmlss	234
cj5lq07o10002315oy6olanzq	234
cj5lq0fr400042c5h3v2z6jo3	234
cj5lq2y0m0003315ow6s9qi5t	234
cj5lq30vi0004315oipx62m6e	234
cj5lq33w60005315ome3xfh3d	234
cj5lq38jq0006315oh3ihbpb3	234
cj5lq3iu600052c5hibx3p2hm	234
cj5lq68nx0007315on3o48io7	234
cj5lq6qca00062c5h4hkg8etg	234
cj5lq6yxp00072c5hx7h0z9nv	234
cj5lq744g0008315oxfaw6d5p	234
cj5lq7hgb00082c5hj9sborqi	234
cj5lq7pmn0009315ozslgokmr	234
cj5mrijzd00003c6hic6doada	235
cj5mriyox00013c6hv1okkwq5	235
cj5mrj2io00023c6hwbkq3uwh	235
cj5mrj8g800033c6hljoz4sx1	235
cj5mrjqpj00043c6hkw5b9cul	235
cj5mrkatj00053c6hbbl3g3ni	235
cj5mrklvr00063c6hfm9n017u	235
cj5mrkqu700073c6hjtpuirgj	235
cj5mrli4v00083c6hksy58iu7	235
cj5mrpwq700093c6hxpvj2ayt	235
cj5mrq0xk000a3c6htxftpfj8	235
cj5mrq7fz000b3c6hver24fdv	235
cj5mrqepz000c3c6h23rppjgw	235
cj5mrqnce000d3c6hbnsj6t3d	235
cj5mrqvza000e3c6h9cpibdvm	235
cj5mrqzrq000f3c6h1pnst8y9	235
cj5mrr1vy000g3c6hs4sq2ujs	235
cj5ms4jy5000h3c6h9xbrl18h	235
cj5ms4sst000i3c6h9bzklnm1	235
cj5msej7o000j3c6h4yt4y0v1	235
cj5mseowc000k3c6hpn5xw69g	235
cj5mso1qr000l3c6hee3uu9lo	235
cj5msqpuk000m3c6h4dsaa4yq	235
cj5msqu64000n3c6hqm7fdyv8	235
cj5mszqu2000o3c6h2acqpgiv	235
cj5mt1217000p3c6h4ckx79l8	235
cj5mt1j1e000q3c6hpn09570n	235
cj5mt1qbu000r3c6hntgduoms	235
cj5mt1t9u000s3c6hfqrrq6ed	235
cj5mt1ui2000t3c6ho58167oe	235
cj5mt28q2000u3c6hgyaeyyyd	235
cj5mt2iwa000v3c6h9qut0wk0	235
cj5mt66pl000w3c6hok8i5tvy	235
cj5mt677l000x3c6hfcozkwtg	235
cj5mt67qh000y3c6h8e11bify	235
cj5mt688h000z3c6h46zz033q	235
cj5mt68qz00103c6hxy53pqt7	235
cj5mt69ol00113c6hl6j0g765	235
cj5mt69vs00123c6h4lai6kb3	235
cj5mt6aca00133c6h074fjkmk	235
cj5mt6apy00143c6huhpb3eq9	235
cj5mt6b5p00153c6h9mzs38ko	235
cj5mt6bjp00163c6hot1vq7nk	235
cj5mt7xui00173c6hhbudartc	235
cj5va7j8m00003x68yyy2ac6k	236
cj64zz7pd0000205wkxyiuayy	237
cj6500zl40001205wzrkuxb8t	237
cj65015af0002205wmawhhrre	237
cj65018kn0003205wvrri9bp9	237
cj6501adz0004205w0tbzp0ti	237
cj6501cof0005205w7qrs5rue	237
cj6501i670006205wocooweap	237
cj6503f470007205w7f6y0y5d	237
cj6505dhp0008205wqdnqcfua	237
cj65089ip0009205wkf4octzj	237
cj650an0p000a205w4uai5ks1	237
cj656ys5b0000305ooz7aap5a	238
cj656zgyf0001305os8fet4oo	238
cj656zwhv0000305ojjzxgn87	238
cj6570kvy0001305odt82xky6	238
cj6570rr80002305oi5cvxn9o	238
cj6570t0s0003305olj9b1m90	238
cj6570ty40004305ohqqbvusl	238
cj6577m7z0005305o8zmu918x	238
cj6577ozb0006305oyww7wpca	238
cj659wims0000305om9r195mo	239
cj659wm9j0001305o880cus8v	239
cj6xbz6l300003i5ox3x7ihm5	240
cj6xbzicb00013i5oncmer98a	240
cj6xbzy4300023i5otg53p4id	240
cj6xc0a2200033i5o7093li32	240
cj6xc0cmy00043i5o0wj6mpnu	240
cj6xc8c9g00023i5o0c26uex2	241
cj6xc8yde00033i5oxprp2zbo	241
cj6xc92tm00043i5om3avvto5	241
cj6xc993t00053i5ooqvj7j79	241
cj6xc9cr500063i5oylqpee6u	241
cj6xc9e4x00073i5owuf3o74s	241
cj6xcan9s00083i5ob2vf6b51	241
cj6xcau0g00093i5o5vbigv9b	241
cj6xcbkwf000a3i5om4ru1qye	241
cj6xcc8t3000b3i5oxhvm08w9	241
cj6xccic6000c3i5otycddiwp	241
cj6xccm1a000d3i5o36pyeu6k	241
cj6xccse6000e3i5oukwqzgrr	241
cj6xccupy000f3i5oqshz5evi	241
cj6xccz9q000g3i5o9uisr5el	241
cj6xcdsvh000h3i5o53ybqwei	241
cj6xcdv3p000i3i5o9e4bdqh4	241
cj6xce911000k3i5oi8ea1lrq	241
cj82uwj9y00002y61wve4dp3v	249
cj6xcdx4t000j3i5o7bq2epov	241
cj6xcf9ln000l3i5olneln26d	241
cj6xcfmlu000m3i5ooimy102n	241
cj77hpvv200003s66wopm8i84	242
cj77hqm7m00013s664p0f6aw5	242
cj77hrm7700003x66s1ha7r0w	242
cj77hrwuy00023s663kyvv0bg	242
cj77ht9q100013x66g1b8ysec	242
cj77htg6700033s66xdcf7h9e	242
cj77hw5x000043s66sj8c9mfo	242
cj77hxxy700053s66roampaaf	242
cj77i5i6300073s66rkjeg0dc	242
cj78rqh2j00022y607s3n5mx4	243
cj78rr71x00052y60twri8ubu	243
cj78rrguy00062y60au4eklob	243
cj7q0mac70000305pfziyd5nl	246
cj7q0qac70000305p7zql154d	246
cj7q0rqqs0002305por0l7nl1	246
cj7rcpv2o0002855dyh5jr56r	246
cj7rcpkfg0000305psm4y5ewf	246
cj7rcqa840003855dcq4st85l	246
cj7rcsj480009855dyl13tlt9	246
cj8h7q8y90004315pci3j3zae	251
cj8h7qg670005315pxvqmo3vi	251
cj8h7r3r60006315pxjrhx1jt	251
cj8h7r89q0007315pieou2lvq	251
cj8h7rfo500072c5uacv19ptf	251
cj77hwm5v00023x66x810d2bk	242
cj77i1c4i00063s66g5stoga6	242
cj78rq2jd00002y60gngh7nqg	243
cj78rqf8c00012y60edwsjw69	243
cj78rqmzy00032y60vvzw6k84	243
cj78rqusw00042y60rhxwctqn	243
cj78ru5bd00072y60wt82tfxl	243
cj79812kb00002z5ol979v48q	244
cj7981p4q00012z5o4y5769dx	244
cj7981qoa00022z5oae4ey4x9	244
cj7981r4k00032z5o1g1jnfq1	244
cj7983b7a00042z5okzh1dmbi	244
cj7983dkf00052z5otv0sekl5	244
cj7983fr300062z5ofi0rrli1	244
cj7984hlm00082z5oclcbenon	244
cj7jb80um000a2y5p1cp2hmic	245
cj7t2xbzy0002305p5n8s1ryg	247
cj7t2xvk80003305phryrt14l	247
cj7t2zxw000022a5llzstufvb	247
cj7t324xv00032a5l5kjmms8z	247
cj7t32jz100042a5lkx8jnqq9	247
cj8h7o7fu0001315puvh4mhz0	251
cj798423c00072z5oromtc800	244
cj79865o700092z5onlnprda7	244
cj79868wh000a2z5obddizjdc	244
cj7986gh700001w5lnyxmhtdx	244
cj7jb5zkr00002y5pxmy2jk07	245
cj7jb6qwq00052y5pbis60q7a	245
cj7jbap4400002y5pvtonbq63	245
cj7jccj0500002y5p19vtsm5x	245
cj7jcckq900012y5pafuf6gyp	245
cj7q0qloh0000855dcasqochv	246
cj7q0rjeg0001305p8nd6mdfz	246
cj7q0ri2y0001855d9yjamf7j	246
cj7q0sedt0002855dndnrxquv	246
cj7t2x7p00001305p1j6vkvko	247
cj7g7sj3u00002y5o0be5zkyf	171
cj7g7sn1100012y5o8nsyt0lp	171
cj7g7soyj00022y5osxmt32jl	171
cj7g7srec00032y5owpfn7pwm	171
cj7jb6a6600012y5p1p5slruj	245
cj7jb6hgl00022y5py44bncem	245
cj7jb6m9t00032y5plnkpcrbe	245
cj7jb6oki00042y5piwfqhzoq	245
cj7jb77dn00062y5pmogoi2tu	245
cj7jb79lz00072y5pffmuewa9	245
cj7jb7ckm00082y5pijvfcoon	245
cj7jb7dvi00092y5p83rjwmg3	245
cj7jb9yp5000b2y5pnn3ltev4	245
cj7jbac4b00002y5pg2c7z6s3	245
cj7jbbypz00012y5pe81xzjvr	245
cj7q0sk5p0003855dn0rxkkcf	246
cj7rcmbr20000855dc1f44ha3	246
cj7rcpru50001855d1ojqrvcn	246
cj7rcqhw60004855dy984uejt	246
cj7rcrcix0006855do03obgb6	246
cj7rcri9z0007855du1k8ij38	246
cj87fk4ob0009315p1ppmck76	250
cj87fwv0m000i315p90qxzx24	250
cj8h7nfjf00022c5u3grvar0w	251
cj8h7np0t0000315ppnpqpuz4	251
cj7rcr7ik0005855dywyb72wo	246
cj7rcroyb0008855d96ts07z7	246
cj7rcs4g90000305pszfimtpz	246
cj7rctd9c000a855dcuvjbu69	246
cj82uwpm600022y61ol0qmhrm	249
cj82ux9hg00032y6198bd1hd9	249
cj87fj44m0000315p9etud75d	250
cj87fjdn50001315pwstp2u3t	250
cj87fjgsn0002315pwx3revru	250
cj87fjk380003315p3amzr770	250
cj87fjl330004315pmxy9sax4	250
cj87fjoko0005315p1bi49s4x	250
cj87fjrsf0006315pq50vnksl	250
cj87fjsp30007315p1t2dgz17	250
cj87fjthd0008315p3t9lnyll	250
cj87fkqkg000d315p1q7u255o	250
cj87fkz6d000f315pyz44rd50	250
cj87fl4uu000h315p3mi4muhg	250
cj7t2vebr0000305pzgfvkg3s	247
cj7t2x7gt00002a5l04samucs	247
cj7t2xcb100012a5lvfduj1rd	247
cj8h7obim00032c5ujwpzlplt	251
cj80jzj9g00013f5p86n7bo0f	248
cj82uwn3900012y619yvotgcs	249
cj82v153f00062y617fwayh9x	249
cj82uxp1c00042y618qkn5ueh	249
cj82uzhjj00052y61nw70e6fc	249
cj87fka9h000a315p3zp8sl52	250
cj87fkg63000b315phrfxq7q7	250
cj87fklil000c315pdk4vxtzm	250
cj87fkvfu000e315pe2djpz45	250
cj87fl2s2000g315pxmwa21si	250
cj8h7olm200042c5u88ntk4go	251
cj8h7oqr400052c5uezvxh2ed	251
cj8h7ou9u0002315p6p7p6ncs	251
cj8h7gt0p00002c5umobzdytc	251
cj8h7h8dm00012c5uqrwq25cw	251
cj8h7plfg0003315pgb4pheax	251
cj8h7q25y00062c5ud9oxtdzw	251
cj8h7szs800082c5uieu190js	251
cj8h82er400002r5ugcav6ccj	251
cj8vwjrh20000315pfxsyv1ce	252
cj8vwjva60001315p8fiegqa1	252
cj8vwjw040002315pe8at0xtf	252
cj8vwjx630003315p5u4f3izy	252
cj8vwkct90004315pwj2cdmsb	252
cj8vwkikt0005315ptvn8zy35	252
cj8vwxdsi0006315pb1bjmzre	252
cj8vwxg600007315pjkb36lke	252
cj8wrdub600003d65v5wxnngd	253
cj8wre70800013d65pjd35gk1	253
cj8wre9u200023d65f1gi79lo	253
cj8wrebyr00033d65dh0oy16j	253
cj8wrekeo00043d65brwbtjfk	253
cj8wrez8h00053d65ffk0nrxg	253
cj8wri6b200063d65bd7t8m2i	253
cj8ximo1n00002r5uujivoe4j	254
cj8xims9g00012r5utnupg1jo	254
cj8ximtqx00022r5ukb4dxt9l	254
cj8ximw8i00032r5uycym97hy	254
cj8xin3vq00002r5uws9ibris	254
cj8xin4n500012r5ulqvtfi0d	254
cj8xin54900022r5uq00ksjb3	254
cj8xin64z00032r5uothwc5mo	254
cj8xin72w00042r5ufotlakfx	254
cj8xingdt00052r5ukfwme1ma	254
cj8xiodg100062r5u5ogueyuq	254
cj8xitk5n00072r5uyt2cuvj4	255
cj8xivhdk00082r5uwnk39bkx	256
cj8xivkl600092r5u2sge3raj	256
cj8xivoca000a2r5umy9f58dg	256
cj8xivrbl000b2r5u1mqeh4m5	256
cj8xivzkt000c2r5ulu83rtdq	256
cj8xixg6x000d2r5uu32kjkla	256
cj8xj1s2q000e2r5ujlu2e9m3	257
cj8xj6yu800002r5uw6pvw5x9	258
cj8xj7o4400012r5ur1osaxkt	258
cj8xj7s9s00022r5uihrsuqtu	258
cj8xj7uk700032r5ub91qekmo	258
cj8xj7zcv00042r5usa47inhp	258
cj8xj80yg00052r5utgctaefm	258
cj8xj82sl00062r5ujbivr1e6	258
cj8xj8dvw00072r5umbenjl4a	258
cj8xj8jhz00082r5umizp9x73	258
cj8xj9j1f00092r5uug3ro32e	258
cja9iql3l00003j5umcnm6bh1	259
cja9irqi100013j5utt9hcjc2	259
cja9iscx200023j5u1i5a2rgn	260
cja9issv700033j5ursvj84h1	260
cja9isvk400003j5uc6x0o1hz	260
cja9it7yz00003j5utd1y7mf9	260
cja9iufx500043j5uoe2dl32y	260
cja9iuscn00003j5u5iqih7sp	260
cja9iut6j00013j5uspcwfshj	260
cja9uao4600003u5uupch92me	261
cja9uau5100013u5u098c1rbi	261
cja9ubxmj00023u5uajhxqr0n	261
cja9ucpba00033u5ug2xxa9e2	261
cja9uctvr00043u5uadwajcjo	261
cja9udyxw00053u5u01ax8r1s	261
cja9ue7at00063u5u26ldqtkh	261
cja9uegqx00073u5uzkl16bbv	261
cja9ueyqk00083u5ugrx3myeb	261
cja9uf7y700093u5ub6afyvrg	261
cja9ufeqs000a3u5ut7cqdiga	261
cja9uflo8000b3u5urxo3yma8	261
cja9uft4d000c3u5ucre3hbg1	261
cja9ugfzf000d3u5ui6izmd3m	261
cja9uglan000e3u5u1px3ibpc	261
cjac91bly00003h5p0ufh7kcr	157
cjac9322i00013h5pjedid51b	157
cjaf37km600033b5xn4bfon3r	262
cjaseooe500002y5qa088q3t5	263
cjaser7o500002z5nqdxjlz41	263
cjaserstq00012y5qra1hb0kx	263
cjaseq5id00003b61k86ee0bc	263
cjaseso2f00002z5n5m328ckj	263
cjasesq2a00002y5q1r3l8rn3	263
cjaseu0ye00012y5qqi4k9cop	263
cjasexn5200022y5q6gfety4b	263
cjasexpi400032y5q395fb6r6	263
\.


--
-- Data for Name: password; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY password (id, digest) FROM stdin;
1	\\x243261243130246b785437757a4f6d31574873444d4635374637574d753748746e392f4c414f696d745357326c6a56635747656557374d45516e5765
30	\\x243261243130245958586e6759387a70346b6c65784948777a5a3674753772636e495231536f75766f59566e4b337959724b58476b2e74382e514e6d
31	\\x24326124313024496f596e7543692f6d6331722f6764596e4b2e3752755a3753687742773830453968506456767755555970686b745452425547364f
32	\\x24326124313024706f504d764e326f6770634b4f2f5a35674f5975564f37476d586f5337524266386a37554d3967393546717647714772534c524a4b
33	\\x243261243130246d4f51543645674b4863697958575355744c352f694f364939304b6e436b62376f593077436169616b6659446946684a314c506161
42	\\x243261243130244e466e3466504b365a346957504f7078364f78566f2e72335732326a2f4375734f49386262615a7436376166736744304544412e57
58	\\x243261243130245044626156306a6844556337593942794337722e6f65455230794d6957784a5847506c5463653679596f6f6348687279515a336371
59	\\x243261243130246772674d69712f536175466a5367356232596d38642e7763424a454b6b3249396b6a6a2f3173696a65354f33324c4d6a5232693047
61	\\x24326124313024433433764b7550525858726b4f55736d47644d4f71656b39357a665564422e76316a63474369453038445979592f756d347950786d
63	\\x24326124313024637756374836555651496451705048514352306e42654751523854734d484565584430646d4d33543356555a6f416e4857354e5843
62	\\x24326124313024623571624d7077636b513872696d38346b2f6e597a65707a317849757041662e5241746a75386548796c686b6d6a374d76664d6f6d
64	\\x243261243130246f316652647155742f783334316c532e37467876662e4b44534e6a34756655314d456b77676552352f43416576714d6142727a6343
78	\\x2432612431302465314a393376594e726746384d6748714b394f57622e42496d3448577a506b3078466a392f4b3435683632356952556e4c47475643
90	\\x24326124313024314442735130303932344f4e50504a494159564b4f4f3036457976464357305159737171532f324e30726a6e3431716d58576c342e
95	\\x2432612431302453566f315064696a37477234754256596b367a68756571594875316274444b74706167483536476a4d45776f522f69554742586369
111	\\x243261243130245266313633482e456750546a5069703855756363322e4e393941727367626942336a38424252593470786a4d51637a676f4a646661
150	\\x24326124313024574a746f6b7749366e494a4654795052724a2e456b2e554f306b4663706950555378794b34485773656e6974655766764531434843
197	\\x243261243130246b4d4165374d6d615436554b6b59795a3878683369756f5162326b6877747871344d757870316744363542332e744a533549693253
204	\\x2432612431302436343737384d687261516341746e6f42505576376e75744561727a64365848564c334b76374a4f72724463565678764569526e3053
174	\\x243261243130244756665568453755724a4d7238346b4d424b584868752e5550795a6457304742764d564334317639305959516663612f35415a446d
234	\\x24326124313024742e57796a46594c695178624b77435144766a6636752e695a7831692e695945346656376349396d39694c7142554c6d6f34374669
242	\\x2432612431302452336642504572706e3537447a6b5179416372716a7535572e336461415a675a59356a534b6b375552717139724e4c684d6874716d
283	\\x243261243130246f6943354c73515749676d746e6a6272722f41516d4f316e36666c472e76444645646b373165413657523467446975345a35414861
319	\\x243261243130246b5469534636354a7a414738797a425761466b4f39756c6a4362395a37566f31556f754d2f747a354a79694766366f4d5255754f6d
326	\\x24326124313024696a4b79754535482e54346e69756c6a63746c7868653772737031636d33324a415a6a33716c6c382e597748577366457451763847
\.


--
-- Data for Name: rawpost; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY rawpost (id, title, isdeleted) FROM stdin;
501	Nein, weil Posts auswählt, was angezeigt wird und groups die sichtbarkeit definieren	f
514	select multiple posts and act on list of posts (bulk connect/contain)	f
870	https://github.com/GoogleChrome/lighthouse	f
518	Beim ausloggen und wieder einloggen bekommt man den gesamten Graph geschickt	f
885	registration works with empty username and password	f
894	das ist aber schön	f
899	haha	f
524	Push Notifications	f
526	Web-based: https://gauntface.github.io/simple-push-demo	f
532	Erstmal nicht, da es doch zwei verschiedene Konzepte sind	f
535	Focusierte Posts definieren, was man zum sehen ausgewählt hat	f
537	Die Aktive Usergroup definiert aus welcher sicht ich mit den angezeigten Posts interagiere	f
541	Ideas	f
543	When connecting with the frontend to the backend, include the version number and check on the the server. If they don't match, send browser refresh event.	f
930	ich bin noch da	f
546	Web App Manifest: https://developer.mozilla.org/en-US/docs/Web/Manifest	f
550	https://developer.mozilla.org/en-US/docs/Web/API/Push_API	f
555	update bucket to freeze incoming updates	f
557	Loginform: ENTER => submit	f
559	UX	f
975	dat 	f
566	scala.rx: Var und RxVar verschmelzen, so dass es nur noch Var mit allen features gibt	f
977	is	f
576	Werbung im System wird als normaler Inhalt behandelt und dementsprechend angezeigt und sortiert. Wird sie aber als kommerziell (?) markiert, wird sie abgewertet. Jeder interessierte kann dafür bezahlen, dass dieser Inhalt wieder in die normale sortierung fließt.	f
1004	Was?	f
1006	Waus	f
1033	Sicherheit	f
579	Isomorphismen zu anderen Systemen, so dass es eine bidirektionale Interaktion über die APIs geben kann. Bei nur imports entsteht datenmüll. Bsp: Github issues, Slack, Wiki	f
1043	Abgesicherte Information	f
584	Changes to be discussed	f
587	AddPostForm: In der Höhe wachsendes Textfeld bei mehr Inhalt	f
590	Exist	f
599	Remember Collapsed Posts	f
603	Marketing	f
605	Woost	f
608	Immer das nächste Jahr in der Versionsnummer, das wirkt garantiert modern	f
611	Reproduce and report quill bugs with: https://scalafiddle.io/sf/H6VJQPq/0	f
187	foo	f
1114	TODO	f
626	Crowdfunding	f
1130	Gruppe 2	f
207	apms	f
435	ab	t
210	spamadispam	t
447	test4321	f
cj41xvalb0003315oy5n5zzbe	a	f
1351	Everybody gets all public notifications....	f
268	test	f
1366	Why do you ask for notifications even before I start using the app? \nI clicked focus on one of the elements and now the canvas is empty \nHow can I zoom in/out? \nLooking into Raskin’s zooming interface work, I think it’s called “Zoomworld” \nThis is the book where I first read about it: https://en.wikipedia.org/wiki/The_Humane_Interface \nIt would be great to see some use-cases, some real world canvases \nMaybe look into a nice like e.g. molecular biology?\nThree things I can think: 1. too many popups. May be have a single stream? 2. An idea would be to use it as a Mindmapping. 3. Another would be to see if code linkages can be shown in this format e.g. like the data Doxygen generates.	f
1376	Demographische Angaben	f
387	more	f
389	Wust	f
390	Funding	f
392	Prototype Fund	f
395	MVP	f
397	Paging	f
402	Bugs	f
405	Private Spaces	f
411	Stiftungen	f
414	Usergroups == Posts?	f
416	Kanada	f
417	Visum	f
422	Felix: hab ich schon gemacht	f
cj41xw1b20009315o3lgzpied	a	f
cj41xv8g90002315oo7hjtvlo	a	t
431	Mindmapping	t
1209	viel frische Luft	t
617	post in /r/scala "what are you working on?" threads	f
620	Publishing	f
873	Faktorenraum	f
876	Unterkategorie	f
887	Test	f
896	ja	f
901	hoho	f
933	hallo	f
973	dabei	f
979	prima	f
1025	Cool	f
1035	Privatsphäre	f
1045	Sicherung durch einen Arzt	f
1132	Gruppe 4	f
cj41zl56y00012y5o4ms1txo3	was	f
cj41zl8va00052y5or0rldcl9	asd	f
cj41v85ib00002y5owutkkqrp	renos	t
cj41verrc000i2y5obp8nkpbt	jfsifwiof	t
429	Test	t
1378	Alter	f
1381	Alter	f
1404	Vorerfahrungen	f
1416	Schulabschluss	f
1420	Bildung	f
1438	Schulabschluss	f
1463	Technikakzeptanz	f
432	Test	t
211	spispaspam	t
428	Mindmapping	t
442	Warum Test?	t
204	this is spam	t
142	UI	t
1483	Schulabschluss	f
1490	Krankheiten	f
1493	Sport	f
293	All Spam in here!	t
385	new spa	t
205	spm	t
914	Wann?	t
919	Bei André	t
912	Wo?	t
911	Alien	t
273	Testing	t
367	a	t
286	test	t
208	spam	t
369	b	t
337	Test	t
371	ab-test	t
153	Hover auf Ring-Menüs	t
1504	einzunehmende Medikamente	f
1509	soziales Umfeld	f
1516	Gesundheit	f
1548	Familienstand	f
2204	wust@wust:~$ docker exec -it wustprod_postgres_1 psql -U wust wust\npsql (9.6.2)\nType "help" for help.\n\nwust=# SELECT sum(numbackends) FROM pg_stat_database;\n sum \n-----\n  56\n(1 row)\n	f
1478	Gesundheit	f
1557	Jahresplanung	f
1585	Wie muss ein kollaboratives Mindmapping Werkzeug gestaltet sein, damit es einfach zu nutzen ist?	f
1591	Interaktionsmuster?	f
1599	Erfahrung mit elektronischen Tools	f
1621	Technikaffinität	f
1643	Warum ist die anzahl der connected clients 24 doppelt, als die anzahl der gerade aktiven User 12?	f
1647	Eine Page ist eine Postmenge, die kompakt als Graphselection mit 4 postd arrays notiert werden kann	f
1537	Hochschulabschluss	f
1693	Currently self-loops are allowed in database. Forbid with constraint.	f
1704	Usability	f
1716	Vorwissen	f
1732	Technikverständnis	f
1747	eigene Gesundheit	f
1750	Gesundheitsbewusstsein	f
1754	Alter	f
1756	Zeitknappheit	f
1768	biographischer Hintergrund	f
1774	körperliche Einschränkungen	f
1782	Zeiteinsparung	f
1790	Schulabschluss	f
1810	ansprechende Farben	f
1822	Bereitschaft Lösen von Gewohnheiten	f
1833	Unsicherheit	f
1838	Sport	f
1847	Allergien	f
1882	Wohnort	f
1935	Eine ausgewogene Ernährung ist mir wichtig	f
1952	Regelmäßige sportliche Aktivität gehört zu meinem Alltag	f
1958	Ich gebe meine Daten ungern weiter	f
1968	Meine persönlichen Daten sind mir wichtig	f
1986	Ich sorge dafür, dass in meinem Haus ausreichend Medikamente vorhanden sind.	f
2004	Ich mache alle Angaben zu meiner Gesundheit	f
2017	Bevor Ich zum Arzt gehe, recherchiere Ich selbst im Internet.	f
2034	Ich kenne mich mit Datenschutz aus	f
2046	Ich tracke meine Kalorien.	f
2112	References	f
2114	https://jakearchibald.com/2016/caching-best-practices	f
2151	https://github.com/coursier/coursier#ttl	f
2160	Lebensmittelallergie	f
2200	Server seems to leak connections	f
2222	Hallo Tjark!	f
2226	Ist alles gut gelaufen?	f
2232	Zentuhr Pro	f
2236	Pilze essen	f
2240	Dashboard	f
2277	Durchaus gut.	f
2285	Marktanalyse	f
2289	SaaS	f
cj3umo52t00022z5ksf0qivvq	und vor allem why?	f
2303	Konkurrenten	f
2305	https://stormz.me/	f
2310	Fonts	f
2314	https://fonts.google.com/specimen/Rubik	f
2321	only once!	f
2327	Marktanteile	f
2332	Marktvolumen	f
cj3umvpnz00042z5kprac1f96	shapeless	f
2360	Live vs Offline Mode	f
cj3u58abt0000315laro2hzvl	Urlaub	f
2319	2024 8.5 billion: https://www.linkedin.com/pulse/collaboration-software-market-size-forecast-surpass-usd-vivek-tate	f
2395	Quill	f
2401	inner shadow in containment bubbles?	f
628	Den Rest aus Repo	f
874	Oberkategorie	f
879	Unterkategorie 2	f
890	bei Aktivierung eines anonymen users, propagiere die Namensänderung an die entsprechenden Gruppen	f
904	wie wäre es wenn ein gespräch by default linear verläuft?	f
971	Hier simma	f
1037	Schutz vor Datenklau	f
1049	Lesbare Schrift	f
1120	Ideenpapier	f
1133	Gruppe 5	f
cj41vaiyo00022y5o4p60kptw	New Group	t
cj41v9w6r00012y5oi8ycbcvf	hans	t
1371	Demographische Angaben	f
1383	Demographische Angaben	f
1402	demographische Angaben	f
1405	Demographische Angaben	f
1408	Alter	f
1422	höherer Bildungsabschluss	f
1445	Hochschulabschluss	f
1454	Herkunftsort/Wohnort	f
1468	Bildung	f
1470	Krankheitsbild	f
1474	Unverträglichkeiten / Allergien	f
1487	Medikamenteneinnahme	f
1496	Medikamente	f
1507	Behinderungen	f
1521	Vorerkrankungen	f
1587	Bildschirmgröße?	f
1607	Kontrollierte Variablen	f
1616	Geschlecht	f
1626	Häufigkeit?	f
1636	Was nutzt man?	f
1645	Nur PostId Menge als State im Graph	f
1649	Neue Kante im State fügt fehlenden Post in Warteliste, für die NewPosts weitergelet werden	f
cj3x3hrmg00003k60qqaazy8a	COE Portal	f
2221	Pause!	f
1695	Gemeinsames Brainstormen	f
1706	Vertrauen in Technik	f
2377	o	t
2385	NANA	t
1143	Was soll ich denn schreiben?	t
1179	Jetztt wieder	t
756	hi max	t
2381	Test	t
1713	Benutzeroberfläche	f
1721	Erfahrung	f
1724	Persönliche Motivation	f
1738	Umstände	f
1743	Interessen	f
1758	Geschlecht	f
cj3un0xd300072z5ktbscu6fw	https://www.youtube.com/watch?list=PLLMLOC3WM2r5Ei2mnSHCD-ZD04AXovttL&v=9lWrt6H6UdE	f
916	Donnerstag	t
2370	Human shape algorithms	t
2372	Exemplified in "Recommender Systems"	t
2365	FG	t
1797	Hochschulabschluss	f
1814	Beruf	f
1855	Kultur	f
1860	Glaubwürdigkeit 	f
1870	Vorerkrankungen	f
1876	Betroffenheit	f
1925	Ich geh regelmäßig zur Vorsorgeuntersuchung.	f
1945	Ich mache regelmäßig Sport.	f
1950	Ich vertraue der App	f
1961	Ich konsultiere nur einen Arzt, wenn es mir wirklich schlecht geht.	f
1973	Wenn ich mich nicht so gut fühle, warte ich erstmal ab, bis ich zum Arzt gehe.	f
2006	Ich verstehe alle Informationen in den Nutzungsbestimungen	f
2022	Ich möchte nicht, dass Daten an Dritte weitergeleitet werden	f
2051	Ich rauche.	f
2056	Ich bekomme ausreichend Schlaf.	f
2069	Ich esse regelmäßig Fast Food.	f
2075	Ich trinke regelmäßig Alkohol	f
2083	Aus gegebenem Anlass trinke ich schonmal ein Glas Wein oder Bier.	f
2088	 Ich hatte noch nie einen Kater.	f
2029	Ich esse regelmäßig gesund.	f
2183	Medizin	f
2191	Lifestyle	f
2202	when clicking on very large posts, the response-form takes up whole screen	f
2206	SELECT * FROM pg_stat_activity;	f
2224	Hier mal ein paar Ideen	f
2230	Foodie	f
2234	Todoo-App	f
2238	Scharfer Männerabend	f
2241	Dashboard	f
cj3u591v50001315l6c24rbzv	Wo?	f
2274	Hallo Felix!	f
2281	Es gibt auch Gedanken zu Pizza mit Heilkräuteröl.	f
2287	https://financesonline.com/2016-saas-industry-market-report-key-global-trends-growth-forecasts/	f
2298	https://www.fontsquirrel.com/fonts/museo	f
2308	Font: Source Serif Pro	f
cj3umpp1b00002z5kgh6juv0d	was ist musik?	f
2357	Habt ihr was Negiert?	f
2358	Usability Studies	f
2363	Keine error notification wenn eine db migration gefailed ist	f
1183	Achso	t
2391	Docker	f
2393	Datenbank elegant neustarten: docker kill -sTERM  wustprod_postgres_1; docker stop wustprod_wust_1; docker start wustprod_postgres_1; docker start wustprod_wust_1	f
2398	Browser Cache & Nginx	f
630	https://en.wikipedia.org/wiki/Business_models_for_open-source_software	f
892	Chat	f
907	hallo dies ist ein schöner langer post	f
926	Hallo Felix, bist du noch da?	f
992	Unter unter kategorie	f
1001	nö	f
1032	Faktorenraum	f
1041	Usability	f
1129	Gruppe 1	f
686	Hallo Peter	f
687	Felix und Peter Privat	f
689	Hier können wir reden	f
692	Text ist immer in Rechtecken geschrieben	f
694	Das könnte die falschen Assoziationen auslösen	f
1131	Gruppe 3	f
699	Mein Privater Space	f
700	Hier kann ich mir Notizen machen	f
cj41vlqua00002y5old1dy71o	New Group	f
cj41vcek600072y5o42mphsii	hans	t
727	bla	t
cj41vczrr00082y5olai84qsl	zekona	t
cj41vawu000032y5owdl807bd	New Group	t
724	Test	t
cj41vf5bg000j2y5o4rkqlfwy	pokghf	t
725	hallo dahinten!	t
cj3vi73kb00002z5ktsa4yy3r	Algorithms	t
781	test	f
cj41vlf39000n2y5orhxewfbw	New Group	t
768	Test	t
794	Projekte	f
795	iNec	f
797	CSP1	f
800	Interdisziplinäre Kommunikation	f
803	Soziale Medien	f
805	Wissensmanagement	f
807	SmartIdentification	f
809	Flüchtlinge	f
1234	Startup School	f
1818	Übersichtlichkeit der Nutzeroberfläche	f
861	Ring Menu hover	f
864	Progressive Web App	f
1826	Medikamenteneinnahme	f
cj3vuii5n00032z5zf8i2awu3	Milch kaufen	f
1373	Alter	f
1386	Geschlecht	f
1400	Alter	f
1410	Geschlecht	f
1412	Bildungsstand	f
1414	Geschlecht	f
1418	Bildungsstand	f
1435	Abschluss	f
1452	Vorwissen	f
1457	Geschlecht	f
1461	Gesundheitsbild	f
1465	Gesundheit	f
2249	Test	t
1347	Agreed!	t
2380	Urlaub	t
831	test	t
757	wir koennen hier schreiben	t
728	something else	t
712	Es ist möglich einen Post mehrmals in einen anderen zu schieben	f
723	Hiiiiiiiii	t
734	so wat?	t
738	Hallo	t
758	ich auch	t
750	ist doch klar!	t
822	test als anderer user	t
820	was ist hier los?	t
846	Darum	t
1476	Sport	f
1481	Berufstand	f
1502	Allergien	f
1511	Affinität	f
1518	Allergien/Unverträglichkeiten	f
1524	chronische Erkrankungen	f
1527	Erbkrankheiten	f
1530	Art der Erkrankung	f
1534	Regelmäßigkeit der Nutzung von Apps	f
1554	Ethischer Hintergrund	f
2149	snapshots in coursier updaten: COURSIER_TTL=10s ./start dev	f
988	Oh	t
922	Prometheus	t
986	Ah	t
2366	Interaktion AI und Mensch	t
2368	Algorithm shape decision making	t
1584	Forschungsfrage	f
1595	Erfaherung mit Mindmapping	f
1603	Wie gerne man das Internet nutzt	f
1611	Alter	f
1631	Spaß an der Nutzung?	f
1134	Seminar Faktorenraum	f
1698	Emotionale Faktoren	f
1708	Einstellung zum Datenschutz	f
1711	Faulheit	f
1729	Usability	f
1735	technische Affinität	f
1752	Hobbys	f
1761	Demographische Angaben	f
1794	Design	f
1800	allgemeines Interesse	f
1831	Notwendigkeit	f
1841	Speicherplatz 	f
1844	Technisches Know-how	f
1884	Erfahrung mit Apps	f
1887	Technische Faktoren	f
1896	soziales Umfeld	f
1898	Einfluss auf Akkuverbrauch	f
1908	Volumenverbrauch	f
1914	Benachrichtigungen	f
1930	Ich ernähre mich gesund.	f
1940	Ich achte auf meine Ernährung.	f
1966	Ich achte bewusst auf Datenschutzeinstellungen	f
1978	Ich achte auf die Inhaltsstoffe in meinen Nahrungsmitteln.	f
1984	Ich möchte Krankheiten angeben	f
1994	Ich lese bewusst die Nutzungsbestimmungen	f
1999	Ich trinke genug Wasser am Tag. 	f
2011	Wenn mir etwas wichtig erscheint, hole ich mir eine zweite Meinung ein.	f
2039	Ich bin im allgemeinen ängstlich 	f
2062	Ich informiere mich freiwillig über Gesundtheitsthemen.	f
2077	Ich koche fast immer mein Essen selber.	f
2095	Ernährung	f
cj3px68h8000f315lm0mwdcx0	enuiea	t
cj3px718p00002z60x3dqrqcw	dsadsa	t
cj3px7akt00012z60icqhn757	dsadsa	t
cj3px8gkb00072z603behbv62	fdssfd	t
cj3px5mil00002z60ybys832b	hi	t
cj3px5vyq000e315l7dhyyc34	Test	t
cj3px8c1a000g315la2qjxshp	echt	t
cj3umu4lp00012z5k9dxhv6l7	scala	f
cj3px94r200013k5k3g9c8j7g	Nicht funktionale Anforderungen	t
cj3pzqckp000f3k5kkk8q5qui	blud	t
2418	Der ring	t
2420	Urlaub	t
cj41xvef50007315o0nmrbl2v	a	f
cj41xvf6a0008315obfgpb8hw	a	f
cj41z7jq600071w5wy1agk0k8	sda	f
cj41z7kp300081w5w4vke15b3	sdadas	f
cj41z81ab00091w5w1fblhgxi	zasasddsa	f
cj41z8497000c1w5wvcff56bi	sddsa	f
cj41z84wl000d1w5wak4waew8	asdsa	f
cj3px3hfv00062z6046a7p2nk	woost	f
cj41z8993000g1w5wy0d2zy0x		f
cj41vc24w00062y5o9110r7tp	suprisingly small, the node, is	t
cj3qknejd00002z5kyxjxnx3u	dasads	t
1700	Ängstlichkeit	f
cj41vd53i00092y5o8ksyqfgf	oenos	t
cj3vi78ua00012z5krh5v1uk7	https://en.wikipedia.org/wiki/Feedback_arc_set	t
cj41vg0ao000m2y5oq2cx44hy	genos	t
cj41vfj1z000k2y5o0ctda7vr	 .	t
cj3pzlyg700003k5kpw5yfv46	Blub	t
cj3pxm57i00002z5zfl68o9mf	hans	f
744	stimmt	t
cj3pzmheu000b3k5k97eyqexk	test	f
cj3qknf4u00012z5kry504oqq	asdads	t
cj3qknfi700022z5kxdy8sod4	asdasd	t
cj3qkng7y00032z5kwaw4q0u1	sda	t
2423	asd	t
cj3siy5lu00001w5de826nlhg	Tets	t
cj3siyxcw00011w5drv0yf004	Hallo	t
cj3px2dkl0008315ltmien9ym	a	t
cj3px263x00052z60jjn92u5v	dsadsa	t
cj3px2cu30005315lh1u4z5se	a	t
cj3px22ae00012z60in3vbr39	dsadsadsa	t
cj3px2de10007315l3zawrau1	a	t
cj3pxnl7100003k5k9ypfupiz	Test	t
cj3px2du8000a315lgwz1p4fz	a	t
cj3px27z20001315l8ket4two	haha	t
cj3r9ddgm00014c5v4vzrj92u	H	t
cj3px2e14000c315ldns73ifq	a	t
cj3px20vi0000315lmi3tj0g2	test	t
cj3px2d4s0006315lwd671ggt	a	t
cj3px2bmd0002315l319whabq	a	t
cj3px2fqr000d315lh4qwh0ij	dtrneudtiae	t
cj3px1k940000315lfcssgcgn	hier	t
cj3px1slv0003315lf8q5qmpd	ituaren	t
cj3px1x6400002z60z7pwf6q6	dsaadsii	t
cj3px24rz00032z60ec6ha4z0	dsadsads	t
cj3px1r4o0002315lqrqnt0mq	uditaren	t
cj3px23r600022z60yba300v2	dsadsdsa	t
cj3px25jn00042z60lt3kdnfc	dsadsa	t
cj3px2dpu0009315lj353iwjh	a	t
cj3px2c3a0003315lzf6pgx24	a	t
cj3r9dapp00004c5vc8ou98vh	Sdf	t
cj3px1ou80001315llaeosod5	haha	t
cj3px2dxv000b315ls30b1pk2		t
1368	Jo ☰	t
2412	asd	t
2416	sad	t
2414	asd	t
2425		t
cj3umvbyg00032z5kkir05kds	macros	f
cj3vvr34r00003t5zyab9dtds	test	f
cj3umuexg00022z5ki9cstwo9	https://vimeo.com/217863345	f
cj3vugeid00002z5zqppzn1de	Workspace	f
cj3stwxsj00053i60her1775v	Ideas can be connected by dragging them onto each other and dropping them onto the "connect" target	f
cj3vuhp1v00022z5z0wmlyqb2	Urlaub	f
cj3sty9gh00073i60cr24n6sr	Insert new concepts by clicking anywhere	f
cj3styv1d00083i60iz4375ka	You can categorize concepts by dragging them onto each other and selecting "insert into".	f
cj3vuiok000042z5z77obqoeq	Shapeless verstehen	f
cj3pzmcdh000a3k5kqy2dxp2w	?	t
cj3pzm6ut00033k5kln899ae7	sollte	t
cj3pzm8sl00053k5k24mo5zwx	hier	t
cj3pzmarc00073k5k1qg1doml	und	t
cj3pzmbrp00093k5k37vdw1z9	da	t
cj3pzm9a500063k5km8k1b1w7	sein	t
cj3pzoznn000c3k5k2knahhz2	test	t
cj3pzm2mn00013k5kje2qc99s	Aha	t
cj3su087a000a3i60qifunrxj	Click on a post and click focus, to dive into the post.	f
cj3su0jye000b3i607noyybc7	Click "Collapse" to hide a sub-tree	f
cj3su0v9h000c3i60ajxotps5	You can zoom in and out using the buttons on the left.	f
cj3vvpeax0000315lxxw93kys	Hallo	f
cj3su164l000d3i60dt0srq97	If posts seem unorganized click the "reorganize" ⟳ button	f
cj3su3phl000e3i6035mr5l6h	Anything broken? Click on the "Give short feedback" button on the right.	f
cj3su4et4000f3i60e3wu4x9w	You can switch to a tree view by clicking on the view-dropdown menu in the top-right corner.	f
cj3vvpf0d00002z5xmpmj6emo	hi!	f
cj3px70up00003k5k7f02pyi1	Ängstlichkeit	t
cj3x38civ00013k5ka7w7y69l	Übung 1	t
cj3px2cii0004315lwg4mkiu9	a	t
1660	plo,kl,kl	t
cj3stnjd800002m5pnmr22ysm	Test	t
1321	Hello From Someone	t
769	Test als user	t
cj3x38ief00023k5khquib42r	Übung 2	t
cj3t41kwi00012z5kllqwkht8		t
cj3t41kq300002z5k6id8qq8h	gfgf	t
cj3tydvyn00002z5kp6rgvsti	Papers	f
cj3vylsko00002z5z0kqa3alr	asana	f
cj3tye0yp00012z5kcyk550n2	HyperCard: an object-oriented disappointment https://scholar.google.com/scholar?cluster=15893840208983803530&hl=en&as_sdt=0,5	f
cj3u3bmyw00003k5krhjpu9gp	Marposs	f
cj3u3dlmo00013k5kpv3a4ods	Partner	f
cj3u3dubc00023k5kd697eedt	Marposs Vertrieb	f
cj3u3e0m000033k5k2k45w9mz	RWTH International Academy	f
cj3vymigc00012z5zoo488hjz	Discourse	f
cj3u3eh1j00043k5kd7potwi4	Ausländische Partner / Marposs International	f
cj41vd9o2000a2y5ojwhpu1m6	New Group	t
cj3u3erx800053k5khoxlm5xy	Artis	f
cj3u3g2t400063k5k842tnha3	Inhalte	f
cj3u3ghoe00073k5k9o3nvir4	Berufsbiographien	f
cj3u3h2un00093k5k0uhyaywd	Talent-Onboarding	f
cj3u3hpfi000a3k5k8cfa5usv	Weiterbildung	f
cj3u3jbxx000b3k5kcfl8g6u7	Wissenssicherung	f
cj3u3k5fq000c3k5kuerpvpkr	Dimensionen	f
cj3u3kbf1000d3k5k10jef8v8	Niveau	f
cj3x4tzq700063k5kwz11thn6	void setup(){	t
cj3u3lq78000f3k5k3fk5itvn	Bildungsstand	f
cj3u3m3fx000g3k5kb9sqfnwf	Horizontal	f
cj3u3m4t8000h3k5km7eh1cki	Vertikal	f
cj3u3mci5000i3k5k3i0uziiy	Über Unternehmens-grenzen	f
cj3u3n6fv000j3k5kni5pss6b	Social Media Knowledgemanagement	f
cj3u3news000k3k5krbx24qi5	Wissens-generationen	f
cj3u3oa4b000l3k5k67d4otph	Kulturelle Unterschiede	f
cj3u3ogzv000m3k5kzuuhql9p	Arbeitskultur	f
cj3u3ot2a000n3k5k8kc33cp6	Kultur der Arbeit	f
cj3u3paju000o3k5km2ic9cfh	Gewerblich KMU oder Groß	f
cj3x81cl200012y5omnoiwqek	penos	t
cj3u3gnxz00083k5koj4pickz	RWTH HCIC	f
cj3u3su09000p3k5kizmbof8o	Conrady Consulting	f
cj3x81lhp00022y5ovzapioqf	sad	t
cj3y9qub800002z63bxi79pm2		t
cj3y9s2qh00022z63r1tvww4d	views zeigen	f
cj3y9sn6100032z63irxo9wrw	mehr als ein chat, mehr als todo, mehr als blog post -> alles vernetzen	f
cj3y9sw0b00042z63ptf0s41k	eigenen workspace aufhaben	f
cj3y9t79t00052z63wx43hmds	frage von anderer person poppt auf	f
cj3vvriki00012z5xrg9waq5j	78	f
cj3yaln6j00012d5mtmsfeka2	I would like to have feedback if the submission of my feedback was successful	f
cj3yay9eg00042d5mrxqt9giu	if I rearrange / restructure the graph - the options to connect nodes etc are popping up everywhere. I way to prevent that would be nice (at least give visual feedback)	f
cj3yb1djc00002z63en1rgwk7	collapse all button	f
cj3ybevem00012z63zu5266io	When?	f
cj3ybf70j00022z63bs5je30r	August	f
cj3ybr12c00022d5mt4g79cpe	a way to stop force layout (annoying if everything moves for a long time)	f
cj3yakfhz000b2z638525eekm	Teams collaborating online need to discuss, plan and decide on their work. We are building a new way of online communication that enables teams to structure their communication.	t
cj3yai9gx0000315owln7e9b1	Think instant messaging: a group of friends organize holidays	f
cj3ykgogm0000315orgivkj9k	jooo	t
cj3yl1ejf00022z63gg8tp7fe	Buy new laptop battery	t
cj3yokolq000e315ohl6uh0rr	Cool!	t
cj3yokj1900012b5mtumnex5k	Realtime updates from other ppl	t
cj3stzs4v00093i60nmjofb7t	Focus brainstorming by diving into a concept.	t
cj3stt8lt00003i60pf5j1qlk	SCP Mindmapping	t
cj3stu3sn00013i603e3y4n0d	What can you do here?	t
cj3stulfr00023i60naiq4oba	Collectively brainstorm ideas	t
cj3stvwph00043i60vbv4p8pr	Real-Time multi-scale collaborative concept mapping	t
cj3su7798000g3i6043yab96g	All posts, are visible to everyone on the SCP. So please be respectful with other peoples content.	t
cj3yop6fa0001315o5avvuwew		t
cj3yoqxi80003315o2e4lz2gu	Space	t
cj3yjgf0v0000315oeyxggae9	test	t
cj3umxd0p00052z5khl0jr3aq	https://github.com/underscoreio/shapeless-guide/blob/develop/dist/shapeless-guide.pdf	f
cj3x7zqqt00002y5o2rdc4u0u	asdsad	t
cj3ws75bu00002y5lk8jpta0u	ja?	t
cj3un0smj00062z5k62t4sfet	dotty	f
cj3vuhkhy00012z5zv4llf5cr	TODO	f
cj3vul8p40000315lfa6djk6i	Geheim	f
cj3x3ns2b00003k5krzsuquil	Tutorial	f
cj3vvpt0u0000265evwai3dqi	test	f
cj3u595z90002315lprpavwmr	Wann?	f
cj3u59eh30003315lqk5u3lyj	Juli	f
cj3u5azbx0006315lr07id9ln		f
cj3u5ayfy0005315lmupdjiw4	Test	f
cj3u59q3a0004315lb4f5atkg	Hallo	t
cj3u5l9z20007315lh9t2m1s4	Neu	f
cj41vdeck000b2y5o7kx42nm6	New Group	t
cj3x4xj8j00013k5np9qztf3h	void setup(){ println("by"); }	t
cj3y9lbho0000315obc5atwzl	Wust Video	f
cj3vvqiqq0000265e7wpe1365	test	f
cj3x3pl0800023k5ka9kypfja	Hahah	t
cj3yakrq900002d5mye06dlwb	Did not expect that pressing enter key is sufficient -> was looking for a "Save" button. Maybe create one with a hint that pressing Enter is sufficient or sth	f
cj3yaocc800022d5mepewfqfy	Changed views an after going back to Graph view the pinning and structuring I made are gone :-(	f
cj3yasiso00032d5mx3g4ifw7	need of visual feedback if I created an edge (helpful if I restructure the graph visually)	f
cj3u3kd78000e3k5kz4q2h42v	Zeitpunkt des Lernens	f
cj3ycqs6q0003315o0shc3va8	Vision	f
cj3yasg040002315o3uo4t16p	New kind of team collaboration software that unifies different concepts in one data structure	t
cj3ykgwdq0000315ongzowvwb	uiae	t
cj3u81m7100023w5dxfev62db	Connect me	t
cj3uav0nf00003u5cm2zt00sn	aaaaaa	t
cj3uaytm500025t5c3p753gcj	Hi	t
cj3yldjo400003f56c28j276l	void setup(){\n  // hier kommen die Startbefehle hin\n  size(600,400);\n  \n}\n\nvoid draw(){\n // hier kommen die Zeichenbefehle hin\n  \n  \n}\n	f
cj3x4uzgu00073k5kya5eh586	void setup(){\n   println("hi");\n}	t
cj3yojg42000b315oroqzwrjp	Intersecting containment	t
cj3yoi31i0006315oiavnc2c0	Demo	t
cj3stx6oj00063i60o1a4ofjj	Tutorial	t
cj3um4wse00032z5klh4b7qtk	Confluence	f
cj3uaxqsl00003u5a2kos0h8x		t
cj3stvc9y00033i60eu00ymy1	Organizes ideas in Hierarchies	t
cj3u82fs100033w5dwix8lv0m	Wo else is here? 	t
cj3u80mvu00003w5dqsgfc6q9	Hello world	t
cj3ufoxpl00002z5kjgb5f1ie	Malte	f
cj3ufxet200082z5kcxr7u1u0	Wann?	t
cj3uftf9g00022z5kknf0hc3t	Was wollen wir?	t
cj3uftytt00032z5kykhrx4ap	Trennung von Bar und Party	t
cj3ufu5vn00042z5k3cghntas	Bier!!!!1elf	t
cj3ufub6300052z5kbhy9r327	Bank	t
cj3ufs32q00012z5ka8up4v5a	Bierkellerrenovierung	t
cj3ufugkq00062z5kjqh0a737	Neue Boxen	t
cj3ufv0r000072z5kluu0xzvz	Finanzierung	t
cj3ug8lgo00092z5kw7rs356v	vfgbset	t
cj3ug91l3000a2z5klm7zzirz	grsfd	t
cj3ug9g9u000b2z5kleq0bmhy	Flurbierkeller	f
cj3ug9vq9000c2z5kdglisto7	gdnbh	f
cj3ug9zm2000d2z5kpia2c70x	reasgrtfh	f
cj3ugdizm000e2z5kczg5j8ct	htdr	f
cj3ugdnkz000f2z5k6glg8qzk	thtfh	f
cj3ugdq09000g2z5ktnzev7jf	htrfdh	f
cj3ugdq9m000h2z5krlb1pceq	drz	f
cj3ugdqkn000i2z5klqhs6cx3	jhddz	f
cj3uge7b8000k2z5k5id7h1ui	abc	f
cj3ugz9vw000l2z5ku971y273		t
cj3ugza0y000m2z5kgl2ugbef		t
cj3ugza5u000n2z5kb77c4mve		t
cj3ugzaat000o2z5kcgj05psj		t
cj3ugdqq4000j2z5kgaemrdl1	jdr	t
cj3uh1ky7000p2z5krbssybae	ohg	f
cj3uhi48r000q2z5k3r0dzkde	htrhrshz	f
cj3uhn8ot000r2z5ka6q38e26		f
cj3uhn8sy000s2z5kjm76wj8a		f
cj3uhn8wr000t2z5kowxzx6bh		f
cj3uho443000u2z5krzazottu		f
cj3uho48v000v2z5k3s0wj9dz		f
cj3uho4cs000w2z5kix4tg6fd		f
cj3uhosu9000x2z5kv744x5l2		t
cj3uhow2h000y2z5k9tox6jzp	wrgsetbg	f
2294	Slack confirmed marked share: https://discovery.hgdata.com/product/slack	f
2329	2012: https://de.statista.com/statistik/daten/studie/274387/umfrage/marktanteile-der-anbieter-von-collaboration-software-weltweit/	f
2342	Deutschland 2015 2019: 2https://www.eco.de/2016/pressemeldungen/studie-public-cloud-dienste-sind-die-turbosegmente-der-deutschen-internetwirtschaft.html	f
cj3um0lox00002z5kkoovo1ca	Konkurrenzprodukte	f
cj3um0xn800012z5kr8obr24i	Slack	f
cj3um0ylv00022z5kls9wxn0l	Jira	f
cj3um330s00002z5kb4hv3p5l	Chat	f
cj3um51fg00042z5kcqtjinj1	Wiki	f
cj3um65r800092z5kv1skjgcv	taiga.io	f
cj3um1iqt00032z5k71nn8nvx	WhatsApp	f
cj3um1x8a00042z5kuyadylsh	Issue Tracker / Projekt Management	f
cj3um5nd100062z5kfvafd25t	Reddit	f
cj3um46jx00012z5kttwift1z	HipChat	f
cj3um347000012z5kt730m1us	Signal	f
cj3um3xlw00002z5kckzzczs0		t
cj3um3pts00032z5kcc0fy6ah		t
cj3um4fqb00022z5knz9ieufs	Wikis	f
cj3um36gt00022z5kbocl79vl	Mattermost	f
cj3umgk3j000e2z5kv5hgbkap	Workflowy	f
cj3umg46b000d2z5k9f9ken9z	Quora	f
cj3umfxmw000c2z5kl10uazp7	IRC	f
cj3um5luh00052z5k3g1mjg9y	Forum 	f
cj3ume9om000a2z5kn4akqeo4	github/gitlab issues	f
cj3um5opt00072z5krz63ceih	StackOverflow	f
cj3umelfc000b2z5kwomncj7c	HackerNews	f
cj3um5rnk00082z5kvnb80rvm		t
cj3px6ezb00012z60m8l9qkma	dddfa	t
cj3vvv7kn00032z5x8kp51tix	stimmt!	t
cj3vvtgcd00022z5xs55ataqo	hallo	t
cj3x4wq6600003k5nbi6mfr1d	Hallo Test	t
cj3y9ooxp0000315o2urjuxc3	Drehbuch	f
cj3vvwfs70001265ewjw0d1no	ich bimms vong de schule her	f
cj3vymxdu00022z5zu8ecbkeb	Trello	f
cj3x16hwe00002y5l0dffj95v	Hallo, wie gehts euch?	f
cj41vlufn00002y5o1rmvcbk9	dihjfdiou	t
cj41y9sef000a315odcjnpden		f
cj3yalbyv000d2z63igk0wljm		t
cj3yb7nx400002d5medd8ezml	undo button	f
cj41zl3h700002y5o51lf2q9f	test	f
cj3ybdlro00052z631bc9o5u0	Paris?	f
cj3ybek2e00002z63l8xz0ysv	Repair bike	f
cj3ycrcbf0004315o44un4a20	Connect Humans in a structured way that reflects their thought structure	f
cj3yal6ad000c2z63zedjkeaj	many platforms for several use cases 	f
cj41zl67800022y5oa9j159ng		f
cj3ybchm700012z63rsd494sg	Holiday planning	f
cj3yjl9ih0000315okm62gk1v	New York?	t
cj3x3pmjo00033k5kupwbvj3q	ahh	t
cj3umm81800012z5kb0afyub3	Fragen ohne Antwort	f
cj3umlabf00002z5k2phb1xic	who let the dogs out?aaaf	f
cj41zl75f00032y5oq1izxu61	a	f
cj3ykh4g40000315o71fzvrac	aeae	t
cj3yjoenn00012z63k96z76yj	too expensive, isnt it?	t
cj3ylez4q00013f56rtnf9xvj	Übung 1 	f
cj3yoij0b0009315opzcm7yig	Here too	t
cj41zl83p00042y5ouyt889xe	asd	f
cj3you64z0004315o72udoxut	Woost Demo	t
cj3yoxdqc000d315o8jtmwqlf	connect	t
cj41zlacz00072y5oyq4tk5pd	dwq	f
cj3yoxbdj000c315oag9a73uk	separate creation fields	t
cj3yowamx0009315o3brhe0vn	Demo	t
cj3yow8b00008315owqxdjqun	Woost	t
cj3yowji5000a315ofme37z47	Another post	t
cj3yoxkdp00022b5mi9cdiws3	realtime updates from other users	t
cj41zlc5u00092y5omctv6q5e		f
cj3yp7vmw000h315ovcw5da4z	Create Post inside	t
cj41zlbcw00082y5oz3tatntp	wqd	f
cj41vb7x900042y5okrpymy6s	New Group	t
cj3yp8g4n000j315o7c6v9afw	connected	t
cj3yp854k000i315ocuypbzif	inside	t
cj41vbuqp00052y5orxbm8bk8	rneoimdn	t
cj3ypbbnx00082z63sbjltsby	he	t
cj3yxpdsk00002y5ozi70x96w	the founder	f
cj3z0pq3l0003315ojtlcgoxo	C	f
cj3z17ige00012y5ohic1gref	v2	f
cj3z1badj00042y5o7x4tffag	start discussion about holiday	f
cj3z1d5e700072y5o1vv10juw	discuss holiday-location in mind map	f
cj3zixwju00023k5ncv1j2t4w	Übung 1	f
cj3yjgirn0001315o0yqujvws	haha	t
cj3zj0br900003o51q7sifdoc	Hallo	t
cj41vdkwh000c2y5o6xdnah3v	hans-0dieter	t
cj41zl9gn00062y5o5htl4934	das	t
cj41zkbom00002z5nlnrvrj77	low hanging fruit	f
cj420qd0p00002y5o8y2esg6m	New Group	f
cj3x384mo00003k5kqd2u1q2c	Processing Tutorial	t
cj3y9thds00072z633owpf7nw	zuklappen	f
cj3y9tnle00082z63ddzhqaxx	was anderes machen	f
cj3y9vu2z0004315og00g6y0e	Have one or two sentences that clearly and succinctly explain what your company does. (see examples above)\nState the problem or pain point.	f
cj3y9vzut0005315ocykl4l8j	Help the viewer imagine how your product definitively solves that pain.	f
cj3y9wii90007315of230mjym	I've also created a new Presentation Day Vids channel in Mattermost (https://chat.startupschool.org/course-3aceae29/channels/presentation-day-vids). Come share your videos, and give / receive constructive feedback. I'll do my best to answer your questions there, but apologize in advance if I can't give all of you individual feedback. However, we can all help each other!	f
cj3ybberl00002z63a62bstta	Workspace	f
cj3y9p9u60001315o8hwy4ik4	Richtlinien von Startup School 	t
cj3ycszf00005315ov1d6kvdu	Kurz vorstellen	f
cj3yjlp040001315otrho96ex	oh, you're right	t
cj41z82us000a1w5wnmg5g4mh	sadsda	f
cj3yjlioh00002z63si2p5gj2	is this too expensive?	t
cj3yavc1d000e2z637oorbgm4	drop position is not mouse position but center of post	t
cj41vdo27000d2y5ovtekodk8	New Group	t
cj3ykygne0000315o8olggsl3	New York?	t
cj3ykyzke0002315oxxufn80x	oh, you're right	t
cj3ykypss0001315ozrw45utf	September?	t
cj41vdzb5000e2y5oss94fu4a	hans dieter penoszopf	t
cj3yl0jwa0003315o2kmz04a4	New York?	t
cj41voc2200012y5oq4k0dbpc	New Group	t
cj3yoh6dn0005315ovicjvk5r	Demo	t
cj41zlsx000012z5nm2ok51id	put "press enter to submit."  into placeholders	f
cj3yol1n800022b5mef1s33jc	Delete me!	t
cj3youfci0005315owjqlj8o4	We are inside another Post	t
cj41zqeg800001w5w444yxk9b	feeed	t
cj3yoxnbb00032b5m6xn80z12	another update	t
cj4205ymx00022y5otctmpcqm	saddsa	f
cj3yozfn200012z63seew176g	Make slides	f
cj3yoy8wt00042b5mslzlnn2q	delete me!	t
cj3yowszj000b315ods4mnj8l	Posts are the same as Tags	t
cj3yp5vss00032z63hhqb05b4	Ask Jenny for help?	t
cj3ypaft000072z63emx3c5rr	more pictures?	t
cj3ypblna00092z63hez6ge89	more pictures?	t
cj3yp7q3q000g315ouc4b01r6	Woost Demo	t
cj3ypdjgp000m315ozyqyty8p	Woost Demo	f
cj3yxsogq00012y5oj52653cm	edit/response menu in graph does not reliably detect when unfocused. sometimes blur on textarea (edit post), does not set it back to div (from textarea).	f
cj3yxsu1e00022y5oo17a4kxu	enter in feedback form!!!!!!	f
cj3z178uv00002y5occ6exix2	keep size of collapsed like uncollapsed	f
cj3z1aign00022y5oqgwmizia	start in chat view	f
cj3z1c2w900062y5onte6yzwg	click on it -> goes to graph in new window	f
1171	Du kannst auch zwei sachen Verknüpfen 	t
1219	Unterkategorie	t
1160	Ja volle Kanone	t
1194	Neuer Punkt	t
1145	Was du willst :)	t
1175	Man kann hier MindMapping machen	t
1201	Vorteile des Stadtlebens	t
1141	Hier kann man schreiben	t
1196	Einfach so	t
1203	gute Infrastruktur	t
1185	Wo warst du so lange	t
1149	Kannst du mir hierdrauf antworten?	t
1207	Vorteile des Landlebens	t
1222	Thema	t
1217	Oberkategorie	t
1166	Was will ich denn?	t
1155	Cool	t
1177	Du bist ja gar nicht hier	t
1151	Glaub schon	t
1140	Hallo	t
2383	Jo	t
cj3wrwrh500003e4y8lqy51tb		t
cj3x4n3ry00043k5kxem8f6k9	Processing Tutorial	f
cj41zuuxm000j2y5otr23qsh9	dsadasasdassasdadsadsadsa	t
cj41z6fri00001w5w4tbubb74	kl	t
cj41z79bf00041w5wkfogasxx	saddsaasd	t
cj41z7a1000051w5w6d6jt0n8	sadads	t
cj41z71pt00021w5wzf0mts6v	sda	t
cj41zoh8w000a2y5o3egljeuf	asd	t
cj41zoktl000c2y5omqj61qb1	sddsadsa	t
82	Woost Feedback	f
cj40bygmn00003i7tv748iwio	Testing spam viw	f
cj3x5mqv000002y5opqgqunku	New Feedback	t
cj3y9rx1500012z63jv6actr6	diskussion zeigen	f
cj3y9tcba00062z63reun8i8b	auf frage antworten	f
cj3y9vpyt0003315oe0tn5y6n	Quickly introduce the founders.	f
cj3y9w4da0006315owv19fzns	A few sentences on relevant qualifications of the founders is also helpful. i.e. we're solving food delivery; we've worked in the food delivery industry for x years and had this problem ourselves.	f
cj3y9vbjz0002315ozvun1jz0	Quickly introduce the founders.\nHave one or two sentences that clearly and succinctly explain what your company does. (see examples above)\nState the problem or pain point.\nHelp the viewer imagine how your product definitively solves that pain.\nA few sentences on relevant qualifications of the founders is also helpful. i.e. we're solving food delivery; we've worked in the food delivery industry for x years and had this problem ourselves.	t
cj3wrzvdz00013e4ys14vp22i	haha	f
cj3ya6mfg0009315ozjq4e2gf	Problem Erklären	f
cj3yacf4z0000315o2npoqf8h	Intro	t
cj3ybcmsg00022z63d6q4aml4	TODOs	f
cj3ybcza600032z63e3yoh309	Buy milk	f
cj3ybdg1i00042z633t79imgv	Where?	f
cj3ybdzfs00062z63c0c6g0og	Rome?	f
cj3z1bo4300052y5ojmmpa60e	someone asks: but where do we go?	f
cj3z1dmr100082y5oayyzfvl7	see how deletion/creation is all visible in both views	f
cj3z1e0vm00092y5obuks7pl0	later come back for article view, to see what was actually decided and talked about	f
cj3yd26sa0007315ofrkz826z	We want to improve team collaboration	f
cj3z25a980004315oks732tv4	on delete ask if the children should also be deleted	f
cj3qkn1p300011w5ypbdmg6mn	Geil	t
cj3pzqbo9000e3k5kewgr3hao	bla	t
cj3pzr4pc000o3k5knla9026z	test3	t
cj3pzr24o000n3k5k8c5a9smv	test2	t
cj3pzqemw000j3k5khswadle9	ölkjasd	t
cj3ydfq4h00052z637agduxgr	Therefore, we are building Woost, a tool that introduces a new paradigm for structuring communication	f
cj41vxf6s0001315o3ivubg1v	New Group	f
cj3ye4vql0001315orhzrefo0	people loose track of the current state	t
cj3ye84tw0002315oc98yaidq	append only	t
cj3yjo2qi0002315od9o38swp	New York?	t
cj3ykyqk300002z63y0ytul5d	quiet expensive, isnt it?	t
cj3yl109s0004315ok3xv68fv	oh, you're right.	t
cj41z83m2000b1w5wg46018co	saddsa	f
cj41z861o000e1w5wocy9etxg	asd	f
cj41vefuw000g2y5ovydadho1	New Group	t
cj3yoifv60008315ocyin1glw	Another one	t
cj3yoicsf0007315o9sl0vb7c	Create multiple Posts	t
cj3yoiym8000a315ord9pt207	Container Posts / act like tags	t
cj3yoiyp900002b5mnr97ka18	Here I am	t
cj3yom01700032z63t14a7w56	Buy milk	f
cj3yolsjb00022z63rqm18xfx	Repair bike	f
cj3yoldvm00012z63hxrqt2yf	My TODOs	f
cj3yol4zl00002z63xs0bgvp3	Space	f
cj3yonipk00042z639jbpw9oe	Order new passport	f
cj41ve5fx000f2y5olb54xt0w	New Group	t
cj41vvvxp0000315opl18wk3g	Feedback test	t
cj41z6yg900011w5wsbnkcd0b	sad\\	t
cj3youmik0006315oxeo92spt	I can create posts anywhere	t
cj3yov4bf00002b5mhrhai798	realtime updates from other users	t
cj3yovcfs00012b5md954j8vc	another update	t
cj3yoz9zo00002z63gpnsd9wv	Demo presentation with team	f
cj3yoyr50000e315oxdelpoqy	Find the online Demo at	t
cj3ypd44q000k315okzr0ty1j	aeuiae	t
cj3ypduwk000o315o223istw5	connected	f
cj3ypdzl8000p315o63tu2rjh	parent	f
cj3ype2wk000q315ouwxnli4v	child 1	f
cj3ype5sf000r315oi6jplfjd	child 2	f
cj3ypecz6000s315o5nf6k5ow	parent 2	f
cj3ypemy200062b5m1xez9fvb	realtime update from other users	f
cj3ypetn600072b5mzmimh6yu	update 2	f
cj3z0pgqw0000315orq2w1wcq	Jo	f
cj3z1avwp00032y5oib91irwn	fake authorship and arrival time	f
cj3qkmxk100001w5ymw0cxjt7	Jooo	t
cj3pzqf42000k3k5kbfyedx0r	ölkajsd	t
cj3pzqb61000d3k5kxdom6ao4	G	t
cj3pzqgd4000l3k5ka62uqaz3	Dkaka	t
cj3pzqda9000g3k5ktvwnwgxf	kasdö	t
cj3pzqyvs000m3k5kzcfj1nkz	test1	t
cj3pzqdrc000h3k5kb3aawjoz	aslkjasd	t
cj3pzqe6g000i3k5k2wie3u3e	ölkjasd	t
1082	Wenn ein Knoten "highlighted" ist, sollte er nicht vom Ringmenü überlappt werden.	t
1092	Z-Ordering: on-Select z-index = 255 ;)	t
cj41zu1p4000d2y5olsa41eh2	dasads	t
cj41zqhha00011w5wfqggb6gb	sadds	t
cj3vvuw9i0000265e5rxc30e1	bye	t
1573	https://docs.google.com/document/d/1kRxp4BMwbE72ZqmXGn7zvmHz2miaRSdKvlC89jZ-D84/edit?usp=sharing	f
cj3wrzz1x00023e4yt7xx77lf	nein	f
cj3vvvtjw00042z5x2wcbnjf8	warum schon gehen?	t
cj41w0zwg0000315oy8uph3b2	New Group	f
cj3x5tb2200012y5ov5ncedwt	Dann aber mit Shahin	t
cj3y9xvbx0008315onn0jdyia	State the problem or pain point.	f
cj3ya6mxd00092z63ublkrpst	einleitung	f
cj3z2w33r00012y5oi4luoeyn	solved	f
cj41xv4iy0000315odxh85ov0	a	f
cj3ws6oiw00033e4yvn1fx5h0	doch	t
cj41xvdl10004315oyop8xs77	a	f
cj3ybfbgw00032z634h39zk8g	July	f
cj3ybjhoq00012d5mk33vq5ii	Feature: Confortable way to delete a subset and put all post of the subset into the parent set	f
cj41xve5g0006315ocmvbb5l6	a	f
cj3yct9ef0006315o1waj1vqe	3 computer scientists from AC, Germany	f
cj3ydanh900032d5m33k0cc6j	startup school requirements	f
cj3yddsmg00042d5mxsq2z7af	position sharing for graph view	f
cj41xvdvm0005315okbqqxedn	a	t
cj3yddv7q00042z63qpcykbx3	We want to improve team collaboration	t
cj3ydhtzb00052d5moaqp04qm	button in graph view for topologic sorting	f
821	Blaasdd	t
cj420781800042y5o4r69oxxq	sda	t
cj41zqkxm00021w5wonk5lmz5	dasdasasdasddas	t
cj41z7aqj00061w5wdepdt2nw	dsadas	t
cj3yaewkz0001315ol5p3derv	We're creating a new kind of collaboration tool for teams that offers a new way to structure content.	t
cj41y9u12000b315oxw1beii6		f
cj41z876h000f1w5w47y9qois	asdsa	f
cj3ya7561000a315ohp0dibzt	search & exploration in group chats (only search for keywords, date, linear search)	f
cj3ye4gcs0000315ouqy8i3p9	overview gets lost quickly	f
cj3vvprwg00003i5x4lpggym4	Tutorial	f
cj3vvr8x900023i5x9jcfd9zr	Wust is a collaborative multi-scale mindmapping software.	f
cj3vvw8uu00053i5x3djwsp3s	Read the post in the order they are connected.	f
cj3vvrx5g00033i5xt3pobe8v	All posts are visible to everyone and should only be modified if necessary.	f
cj3vw56gl00003i5xex5h5kxf	Article view ist cool!\n	f
cj3vvqv1q00013i5x1pv6w796	Wust - Rules	f
cj3uasvtf00005t5ceor3ujy9	Wust is cool	t
cj3uat9rs00003w5c7zhyzwjp	create new post	t
cj3u811mw00013w5dv6gddcl8	I am a post	t
cj3uayqsh00015t5c5a50wxw4	Hello	t
cj3x4tq4o00053k5k6alnuxos	Übung 0 Standardprogramm	f
cj3yl0swu00012z634bm7xq5e	quiet expensive, isnt it?	t
cj3yojwwv000d315owvzisn94	Inserting	t
cj3yojrmk000c315op7d3rtv2	Responding	t
cj3yooieh0000315o5ymc17d2	Demo	f
cj3yoqqv00002315ojwn1kxjv	Woost	t
cj3youxlg0007315o4ygsgs1w	inside another post	t
cj3youmtx00022z63655c9sj0	Borrow screwdriver from Jim	f
cj3yotvxc00002z63dowma3fo	Buy new tire	f
cj3yoq2wi00012z6313klqyju	Prepare Presentation	f
cj3yow0l200032z631ialegtb	Demo presentation in team	f
cj3yoqz4p00032z63hpz5mc4m	Make slides	f
cj3yoqnf400022z636amvyizf	Sync with Peter	f
cj41vek5u000h2y5ogj59gqnk	New Group	t
cj3yoyx18000f315ouv2mhyuh	https://woost.space	t
cj3yp1raw00022z638zvcjl7b	Buy milk	f
cj3yp9s8200042z63hddqhl4g	cxz	t
cj41zu4gt000e2y5o02taqw7a	dsaads	t
cj3ypa13600062z638ve8dffv	ds	t
cj3yp9zc500052z63cwzk52qe	as	t
cj3ypd99y000l315ohkmnw8pq	Woost Demo	t
cj3ypdo95000n315onwovsrad	inside	f
cj3ypek8f00052b5ml43detqk	Update	f
cj3ypexv100082b5m17t5qwq7	delete me!	t
cj3z0pl1b0001315orbt5374l	A	f
cj3z0pnte0002315ox1zoq7k0	B	f
cj41zu77x000f2y5opsurpr5a	sda	t
cj41z8ben000j1w5w6e2jgz5h	asd	t
cj41z89zy000h1w5wjyo71991	sda	t
cj41z8bx0000k1w5wha818za2	asddsa	t
cj41z8ch5000l1w5wd5uz6ryi		t
cj41z8aov000i1w5wd0yzrjhw		t
cj4206bks00032y5obpmk94mu	dsads	t
792	Hallo	t
cj4207f3z00052y5oufn4m1a0	dsa	t
cj41zumfx000i2y5ok0htul1q	das	t
cj41zuko8000h2y5otc7aadgr	das	t
812	Scheint ein Problem zu gebensda	t
cj41xv72q0001315ov746a0i8	a	t
2296	2020 8.4, 2015 7.1: https://www.appsruntheworld.com/top-10-collaboration-software-vendors-and-market-forecast-2015-2020	f
2317	2021 49billion: http://www.prnewswire.com/news-releases/enterprise-collaboration-market-worth-4951-billion-usd-by-2021-609772265.html	f
cj434jmx900002y5ocxsusnsl	New Group	f
cj434kidc00012y5o3krnshwc	sdadsdaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaas	f
cj4444owi00003k5nj5lii8ok	Projekt Internationalisierung der Berufsausbildung	f
cj444p6rk00013k5ngce5tpit	Projekt OrgaMe	f
cj444psd400023k5nmdtplsu7	Organisationale Ambidextrie	f
cj444q1a000033k5nitov4j9a	Effizienz	f
cj444q3e700043k5nx0u2euzh	Flexibilität	f
cj444q98v00053k5nq7uf7jt2	Innovationsfähigkeit	f
cj444qhkv00063k5nfuwjdt3p	Integration neues und altes Wissen	f
cj444qvlb00083k5nqfre03bu	skalierbar	f
cj444qy8f00093k5n6enn5ugm	individualisierbar	f
cj444r8ji000a3k5n3dme7yv2	Ziel: Umstrukturierung zum agilen Unternehmen	f
cj444rdfz000b3k5nxlt0enpc	Agilität	f
cj444rjgu000c3k5nexybg1n0	Führung	f
cj444rn1i000d3k5nyu0zzqjn	Projektmanagement	f
cj444rrda000e3k5ng1iiwdr6	Technologieentwicklung	f
cj444tdvg000f3k5n30jthq8j	Sozialkompetenz	f
cj444trnv000h3k5nwsnyeiyy	Mittleres Management	f
cj444ud1f000j3k5n27rhhohz	Partner	f
cj444ui2a000k3k5nieuhtfd1	Groß/mittel/klein	f
cj444us42000l3k5n5f3mmgr3	Kontrolle	f
cj444utdf000m3k5ne21r895r	Misstrauen	f
cj444uuki000n3k5ntbecxjqv	Vertrauen	f
cj444uxia000o3k5n9crfym40	Wertschätzung	f
cj444uywh000p3k5nk4gyrkt7	Privacy	f
cj444v7nu000q3k5n9rr2qnga	inHouse Startups	f
cj444vsf5000s3k5nwokrn82u	Unternehmensübergreifende Kommunikation	f
cj444qqh200073k5nb95ehjay	Methoden-entwicklung	f
cj44567wz000v3k5nhugxcfcu	Prädiktion von Erfolg	f
cj4457w9y000w3k5n1ix4vo5x	Werkzeug zur Berechnung von Reward-Hierarchies	f
cj4459gny000x3k5n066niblx	Drag/Drop Targe von großen Items passt noch nicht ganz	f
cj445a47p000y3k5n82kb5hq2	Click into "Area" sollte Post "insert" sein..	f
cj445l8ox000z3k5nslllxgzw	Wichtigkeit	f
cj444wbjt000t3k5n7gphimvb	BCG Digital Ventures	f
cj444tncj000g3k5nr7hn17h0	Akzeptanz von Transformations-prozessen	f
cj444u6ub000i3k5nubryy020	Dezentralisierungs-prozesse	f
cj4455vbw000u3k5n5v3vauzz	Neue Erfolgsmetriken - Controlling	f
cj444vgmp000r3k5nldgks7bg	Flexible Wissens-speicherung	f
cj445li6p00103k5nij3bqzw2	Wertewandel (Y)	f
cj445ofz100113k5nw3irgj7s	UNDO BUTTON!!!! 	f
cj445s3uf00123k5ns9wcrzll	Campus	f
cj47c2fw900023k63f6o2yxi9	Personality	f
cj3vvvr2f00043i5xdcdfxyuz	Start here.\nClick Expand to open!	f
cj445s79q00133k5nbhsvrjgy	Startup-Reife	f
cj445sa3a00143k5nou2f9thn	eGo	f
cj445scrq00153k5nilefkdjl	Street-Scooter	f
cj445swjx00163k5nlefnvz11	Digitalisierungsgrad des Produkts	f
cj445thys00173k5nzo3r1aug	Digital Maturity Index von Unternehmen	f
cj445tl4c00183k5n0dzbfvpg	Dimensionen	f
cj445v8ga001d3k5n7ve4v6qb	inHouse Startup	f
cj445vjcq001e3k5nho1bx9fm	Reward-Hierarchien	f
cj445w3rv001f3k5nuje3ftuy	Statusorientierung	f
cj445y0u0001i3k5nw1yw180g	Dezentrales Wissen	f
cj445w66z001g3k5nm4b9k561	Machtorientierung	f
cj445z0dk001l3k5npi09pyt1	sozialer Vergleich	f
cj445zsja001n3k5ndr6ic741	Unternehmenskultur	f
cj4465fbx001t3k5nlw25s3ji	Sharing-Knowledge	f
cj446u37y001x3k5nk9ssylw2	Schnittstellen	f
cj445uqoj001a3k5n3xq6e4gn	Digitale Agilitätsmethoden 2.0	f
cj445u3mc00193k5n9nqntirc	Conrady Consulting	f
cj446vene001z3k5npsz4adgx	Strukturentwicklung	f
cj446vneq00203k5n5lnad1mt	Gemeinsamer "Raum" und Schutz zugleich	f
cj445uyj7001b3k5noz4962fc	Individualisierbar	f
cj445v3rv001c3k5n523v8y7s	Dezentralisierung	f
cj445xqy0001h3k5nx3jcyzgf	Dezentralisierung von Kontrolle	f
cj445yps0001j3k5nbejesrc9	Risikoneigung	f
cj445yrsf001k3k5no6wtj9iq	Ängstlichkeit	f
cj445ziy6001m3k5n4sueohrf	Persönlichkeit	f
cj44610ro001o3k5nrq5puuz2	Gehorsam	f
cj44654mu001s3k5nl1o06jy6	Geschäftsmodelle	f
cj446sg32001u3k5n27hq2cze	Objektorientierte Herangehensweise	f
cj446xymg00213k5nmuwu82jj	Kollaborations-werkzeuge	f
cj446yezs00233k5nwowgv617	Interorganisational	f
cj41zoj1e000b2y5oa5rroymg	dsa	t
cj44fe6dt00002y5ouelvvui1	online rwwesources	f
cj4461tsa001r3k5nmedgl13e	Personalauswahl	f
cj44619m3001p3k5novf91zml	Homogenitäts-neigung	f
cj4461kbo001q3k5nv54lmgf0	Trainings-interventionen	f
cj446ttam001v3k5ng0dr045i	Managementmodell	f
cj446u0ty001w3k5n48m8nxvx	Kapselung	f
cj446ub1x001y3k5nixdgz96u	Unternehmensorganisation	f
cj3pzm56600023k5keb17x9za	Warum	t
cj446y9jk00223k5nlklycx8j	Intraorganisational	f
cj3umu1hf00002z5k2fx0rhr3	online resources	t
cj44763pu00243k5n0le6xd52	Vertragsmodelle für Teilen von "Wissen"	f
cj41z77iu00031w5wdv3q66me	sddas	t
cj45kl9ry00163k5n4zp57v54	Beschreibung Arbeitsprogramm	t
cj44ff0xm00012y5o3hdw1cgs	SPAM	f
cj44ffnf000002y5o9cjc6qqg	hi!	t
cj45zmr0600003k6219ggfj74	Bachelor-Plenum SS2017	f
cj45zoqtc00003k60c7o2eg6e	Syntax-Sammlung	f
cj44fl5fi00002y5o96zk0wc5	is there anybody out there?	f
cj44fnwvu00002y5oozvbzivt	it is not visible when two collapsed posts have a non-empty intersection	f
cj44fou2800012y5oclazxqe4	maybe visualize similar to redirected connections?	f
cj44g3gov0000315oe46f97bc	A	f
cj44g3iky0001315o50qz7jsu	B	f
cj44g3kyj0002315ow3xvkerb	C	f
cj44ip7j900003i63wwdm08oq	Larger Mindmaps overlap alot. Allow changing charge	f
cj44iq0v800013i63l8n1z7oo	no	f
cj45jh3ms00003k5nl42coy3j	Cyber-Experiment	f
cj45jj2gn00013k5no8feremv	Pro-Argumente	f
cj45jj5tj00023k5nroug61y6	Con-Argumente	f
cj46l3s4m00033k5nv5p9xoes	Gruppe FilterBubble	t
cj45jk2my00033k5nmepmbp57	Niedriger Innovationsgrad	f
cj45jl2wa00003i6393hvbov4	Methode	f
cj45jl9ig00023i63mdll3jx5	Ethik	f
cj45jlhr500033i63leh02f9y	zu orthodox	f
cj45jmhif00043k5nrrgo2enm	Anwendbarkeit in diversen Szenarien	f
cj45jmkbj00053k5n7kmahkex	Privat	f
cj45jmmwm00063k5n7io37r5p	Beruflich	f
cj45jn2lr00073k5ntkdaulp3	Cyber-Sickness	f
cj45jn59300083k5n99c9kwaq	Ist schon Publiziert	f
cj45jnyk600093k5n8vlblchw	Neue Methodik für VR	f
cj45jodvi000a3k5nlp8ni9ag	Neue Kombination von RDW	f
cj45joop2000b3k5n91dnvkih	Parametrierbare Algorithmik	f
cj45jl6yw00013i63jeg9e0cl	Technik	f
cj45jpnwt000c3k5nkkox610s	Augmented Reality > Virtual Reality	f
cj45jptg4000d3k5npvkg3v17	Universal Access	f
cj45jq2nw000e3k5nlcgwiciy	Weniger Travel (weniger Fliegen)	f
cj45jqyr8000f3k5nbhdimcx0	digitales Gefängnis	f
cj45jr3it000g3k5nktwpuocm	Sucht	f
cj45jrcos000h3k5nstsf1fao	Dissoziation von Realität	f
cj46l2ydl00013k5nurnw06fw	Gruppe RecSys	t
cj45jsu84000i3k5nod8jrrn9	Mehr Realität (durch natural Walking) in virtueller Umgebung	f
cj45ju1mt000j3k5napqxzzkc	Ziele	f
cj45jufn8000k3k5n7t4cad31	Entwicklung einer integrierten RDW-Methode	f
cj45juvec000l3k5n1gbgsmbl	Prädiktive Messung von Cyber-Sickness	f
cj45jv3l8000m3k5n2nhcxulm	Adaptive Steuerung der RDW-Constraints	f
cj45jvik4000n3k5n4p05cntm	Auswahl der passenden RDW Methode nach virtuellem und realem Kontext	f
cj45jw3bf000o3k5nwf0f5lqf	Kultur-Ferne	f
cj45jwdez000p3k5np6yr9s0y	"Abgehängte" abholen	f
cj45jwwcu000q3k5nt70t596f	"Hol die Welt in dein (Kranken-) Zimmer"	f
cj45jxuh2000r3k5nu498hsv0	Remote Presence	f
cj45jxyf1000s3k5nslpp1vuq	Arzt	f
cj45jy0qt000t3k5nw8o2psdo	Architekt	f
cj46l329s00023k5n482sl6tf	Gruppe HRecSys	t
cj45jyccl000u3k5nrapa38bx	Digital Factory Planning	f
cj45jzkn8000v3k5ns7jm09aw	Neue Bewertung von Erleben und Verhalten 	f
cj45k0xu1000w3k5n2txrqf36	Zerstören der inneren Sicherheit	f
cj45k12bl000x3k5njv0rnr37	Identität	f
cj45k165d000y3k5norkzmif9	Realitätsbezug	f
cj45k1afd000z3k5nsr49coi6	Realitätsanker geht verloren	f
cj45k2r3b00103k5nslbc4rdd	Problem ist "nur Trade-Off" und keine optimale Lösung	f
cj45k31a600113k5nmugr8y08	Beschreibung des Trade-Offs	f
cj45k4m0v00043i639dodyjr3	Antwort-Bias durch Sebstauskunftsskalen 	f
cj45kfq6i00123k5nt61ucm1x	Weiterentwicklung von Digitalität	f
cj45kka2p00143k5n4so53zbm	120.000 €	f
cj46lbm0o00043k5nyfouw3o7	Data Cleaning	f
cj45kkdpc00153k5nh87eumev	18 Monate	f
cj45kvvtj00173k5nzeitlpa0	Ideen	f
cj45kw5zo00183k5ncg0fpl2a	Fun VS Cyber-Sickness	f
cj45kwcdf00193k5ndh0nqocg	RDW vs Besoffen	f
cj45l5gyz001a3k5n1i5k8irp	Deadline	f
cj45l5s42001b3k5nhblmvz9m	5.7.	f
cj45kk5ep00133k5nuch51jaq	Rahmen-bedingungen	f
cj46ldrc600063k5nvspxzdjg	Hier kann kollaborativ Syntax für die Bearbeitung der Daten aus dem Plenum abgelegt werden.	f
cj46llo0x000a3k5ncnbsk3tl	Fragebogen	f
cj46lceun00053k5ngaljyobv	* Hier steht ein Kommentar.\nRENAME VARIABLES (oldvar = newvar).\nCOMPUTE newVar= number(oldVar, F2).\n	f
cj46lfi4z00083k5ni0rszo3e	Ich muss in eine Unterebene gehen um Unterunterebenen anzuzeigen.	f
cj46llkv600093k5nx3ohajpf	Fragebogen-Einleitung	t
cj46mgi90000b3k5ng1oma8h5	Einleitungstext	f
cj46mgl04000c3k5nmx058aua	EndText	f
cj46mj5ob000e3k5n4pucde6z	* Code zum Reinigen der Daten der Gruppe Krypto.\n	f
cj46owu3x000g3k5nz0k9sive	Variablen umbennenn	f
cj46oxkr4000h3k5nvjdztcnb	Variablen rekodieren	f
cj46oxq14000i3k5ndf02k4ba	* Varialben umkodieren.	f
cj46peqle000j3k5nu68kiz8s	Zu lange ja oder nein?	f
cj46pzgh90000265kdbn82vus	Bitte antworten Sie möglichst unvoreingenommen. Es gibt kein richtig oder falsch, sondern nur Ihre Meinung zählt. Die Beantwortung dauert ca. XX Minuten. Gerne können Sie uns am Ende über das Kommentarfeld Anmerkungen zukommen lassen.\n\nSelbstverständlich wird in dieser Umfrage großen Wert auf Ihre Privatsphäre gelegt, Rückschlüsse auf ihre Person sind nicht möglich. Die Daten werden nur für Forschungszwecke verwendet und anonym ausgewertet. \n\n\nViel Spaß bei der Beantwortung!	f
cj46mgtyz000d3k5nd2avp63x	Liebe Studienteilnehmer, ...	f
cj479de3z0000295mfi2kaq3s	Geschafft! Viel	f
cj45m0hsb00003k5nsb3da0oe	Article View in Group is empty?	t
cj441o5it00002z5n3u00e75s	testing humanity	t
cj46q469l0000265k8b24sua0	Sie haben es geschafft! Vielen Dank für Ihre Teilnahme! \n\nFalls Sie noch irgendwelche Fragen oder Anregungen haben, dann schreiben Sie diese uns in das Kommentarfeld.	f
cj47c2b8100013k63kzaunblb	Psychological Agent Model	f
cj47c3m4800063k63uu7wct94	Education	f
cj47c3o1b00073k63o15ewlq5	Gender	f
cj470td0900003k5cjq7guk6k	// Gruppe HRS\n// erster Paragraph: "verschiedener Dienste" zu ungenau, Verbesserungsvorschläge?\n// zweiter Paragraph: Es werden nicht nur Szenarien bearbeitet, anderer Formulierungsvorschlag?\n\n\nLiebe/r Teilnehmer/in!\n\nMit der Digitalisierung der Kommunikation kommen zunehmend Fragen nach Datensicherheit und Privatsphäre in der Nutzung verschiedener Dienste auf. In dieser Studie sollen in erster Linie das Vertrauen in (Gesundheits-)Empfehlungssysteme, Cyber-Security und kryptographische Verfahren, sowie die Phänomen Filterbubble und Schweigespirale untersucht werden\n\nDazu werden zunächst einige demographische Daten abgefragt, bevor Ihnen einige Szenarien vorgestellt und Fragen zu diesen Szenarien gestellt werden. Wir interessieren uns dabei nur für Ihre persönliche Einschätzung. Es gibt kein richtig oder falsch.\n\nDiese Studie wird am Lehrstuhl für Communication Science der RWTH Aachen durchgeführt. Alle Daten werden ausschließlich zu wissenschaftlichen Zwecken genutzt. Dazu werden sie anonym ausgewertet, sodass keinerlei Rückschlüsse auf Ihre Person möglich sind. \n\nDie Umfrage wird ca. XX Minuten in Anspruch nehmen. Wir bedanken uns sehr, dass Sie sich die Zeit nehmen, die Umfrage mitzumachen! \n\n\nKontakt: \nAndré Calero Valdez (?)\n…   \n\n\n\n	f
cj47c283z00003k63wmi2t9tj	Agent-Based Modelling	f
cj47c2j7t00033k632swx2y9p	Social Media Usage Behavior	f
cj47c2mgx00043k6331i2es0z	Political Orientation	f
cj47c348p00053k63e8kuvszc	Communicative Model	f
cj47c3v0f00083k63bpvdxxe1	Extraversion	f
cj47c433z00093k63ifq9bz05	Openness	f
cj47c47jb000a3k635b406i1z	Conscientiousness	f
cj47c4efq000b3k63ongr1v3a	Agreeableness	f
cj47c4k1q000c3k6366ndez2r	Neuroticism	f
cj47c4tc7000d3k63hhf4hytp	Network Agent Model	f
cj47c50qu000e3k63ornob9tc	Connection Likelihood	f
cj47c6cn0000h3k63ykkx9bwu	Network degree	f
cj47c76nf000j3k63qxppgdo9	Recipient Behavior	f
cj47c8415000k3k6372d4oo9p	Recommender Agent Model	f
cj47c8q94000m3k63pt64iwv7	Algorithm	f
cj47c8vww000n3k63fdz0asz3	Evaluation Parameters	f
cj47c98s8000q3k63j3zxe72b	Hybrid	f
cj46ovq81000f3k5n4j1ioyi6	* Demographische Angaben säubern.\n\n* Variablen umbennen.\nDELETE VARIABLES CollectorNm to LastName.\n RENAME VARIABLES (q0001_0001 = absicherung).\nRENAME VARIABLES (q0002 = gender).\nRENAME VARIABLES (q0003 = ages).\nRENAME VARIABLES (q0009_0001 = umwelt1).\nRENAME VARIABLES (q0009_0002 = umwelt2).\n\n* Variablen konvertieren.\nCOMPUTE age_numeric = number(age, F2).\nCOMPUTE umwelt = mean(umwelt1,umwelt2).\n\n\n*Variablen umkodieren.	f
cj47c55sd000f3k63nn5dtnv3	De-Connection Likelihood	f
cj47c73ic000i3k63oapfvjsi	Sending Behaviour	f
cj47c9kdj000t3k63he6i4ktr	Novelty	f
cj47c61at000g3k633u7h20t6	Centrality	f
cj47c885l000l3k63kez4j537	News Agent Model	f
cj47c948g000o3k6373fxlrpv	Content-Based	f
cj47c96lk000p3k633v4i0hda	Collaborative Filtering	f
cj47c9fbb000r3k63q6vid8wh	Deep Learning Based	f
cj47c9ish000s3k63dfykh369	Accuracy	f
cj47c9lpj000u3k6384zt7ns3	Surprise	f
cj47c9ozi000v3k633mfcwsxy	Coverage	f
cj47can6e000w3k639ou0lupn	Explicit/Implicit Feedback	f
cj47cc3h8000x3k63u0t5ma7x	F1 Measure	f
cj47ccnub000y3k63ax2658bh	Information Spread	f
cj47ccsde000z3k6358empyis	Relevance	f
cj47ccxkq00103k63t1gozz89	Political Orientation	f
cj47cdc6200113k636jn8o3ve	Evaluation Parameters	f
cj47cenzk00123k63y6t51y2c	Salience	f
cj47cfa3z00133k63o13d5oyr	Social Media Mining	f
cj47cfez300143k63u0rkef7z	Parameter Estimation	f
cj47cfo1b00153k63zi748r2o	Agent Parameter Estimation	f
cj47cfv2600163k63nzj0rwqo	Network Parameter Estimation	f
cj47cg3g600173k63b5trw0zq	Evaluation Triangulation	f
cj47cgc9h00183k63aiajm17x	Usage Motivation	f
cj47chbbw00193k63gwomxa5u	Democracy Prediction	f
cj47chhgs001a3k63adj2u4qs	Stability	f
cj47chk7g001b3k63xisccdgi	Homogeneity	f
cj47cho4z001c3k636ts62nnp	Social Security	f
cj47ci6jn001d3k63ree3bg4z	Connectedness	f
cj47cidw3001e3k63v2wrm15m	Multi-Level Community Detection	f
cj47ciyrm001f3k63kg5m6unm	Graph Entropy	f
cj47d9432001g3k63h5hfj7av	Example Modells	f
cj47d9dhh001h3k63rgehdu8c	Schelling Seggregation Model	f
cj47da92k001i3k63o8688t99	Wilensky Predator Prey Models	f
cj47dbsoq001j3k639zwgk6h2	Frameworks	f
cj47dbw1e001k3k63hq3w0jre	NetLogo	f
cj47dbzip001l3k63qnz9owtu	Mason (Java)	f
cj47dc63t001m3k63twfrv0gl	Migration Models	f
cj47dd46o001n3k63lc426lid	Epidemiological Simulation	f
cj47ddqjj001o3k63w2qk3tbo	Mesa (Python)	f
cj47djvpy001p3k63yonrq5dr	SugarScape	f
cj47dmaob001q3k63jj4sbpwy	Homophily	f
cj47iimel00003i63ghtgg5ex	Stephanie	f
cj47ijpwe00043i63o3cvn96f	Oder auf den Mond	f
cj47ijxdq00073i6331h3zdb4	und dabei Pizza essen	f
cj47ik07100083i63b4xaid1t	oder eine Salamistulle	f
cj47ij5tj00013i63nnt6bc5c	Ort	t
cj47in19100093i63d1o4lvih	mit pffeffer	f
cj47inptk000a3i63hdf26uzm	pfeffer	f
cj47inxc8000b3i63himcjtf2	rosmarin	f
cj47j1njk000h3i63cvvwjx97	ich will die kokosnuss	f
cj47j1kd600011w5if4cvhdr7	jaaaa	f
cj47j1otj000i3i63btbku1db	trinken	f
cj47j1prv00021w5iylfp1pbm	ich auch!	f
cj47j1q78000j3i636wgr73fq	und essen	f
cj47j22q5000k3i63o5aioed5	aber wie findest du das denn	f
cj47iob3h000c3i6384a4rbft	gewürzt	f
cj47j7lqm00002y5o1qaqo33q	make messages in chat clickable to focus	f
cj47j7yrg00002y5ofkhoc3ag	sad	t
cj47j8i2v00001w5w3plkndl9	asdds	t
cj47j9a3u00012y5ochzbuu14	graph view	f
cj47jakaz00022y5oxf3nt31h	article view	f
cj3z2uuvx00002y5oeq96lva6	thank you!	t
cj47jb7ke00042y5otev7ci5o	chat	f
cj47j09lg00001w5iu6uefyww	Hallo	f
cj47iph7f000e3i63wciaxikd	roggen	f
cj47ijs1500053i63n820dep5	Mit einem Raumschiff	f
cj47ipe6j000d3i63v62vjplp	brot	f
cj47ipljo000f3i63tcyr5r83	toast	f
cj47ijuy700063i635qa4ipyq	oder einem Fahrrad	f
cj47ips5e000g3i63qt4snml4	brezel	f
cj47ijn5a00033i631mlon7am	Oder nach Trinidad Tobego	f
cj47ijjms00023i63639a7mrv	Nach Norwegen fahren	f
cj48nwrvl00003i5nrd4qsivr	Andrey's Mind Map (tm)	f
cj48nx7uo00013i5n5lx9jqt0	Thoughts	f
cj48nxbuh00023i5n72zfdg6y	Thoughts!	f
cj48nxekv0000315oev0k3hbp	New Group	f
cj48nxhrn00033i5n3uane530	more thoughts	f
cj48nxkxf00043i5nuxhn9lqw	even more thoughts!	f
cj48nxtoz00003i5n11fju63i	Thoughts!	f
cj48nxw7y00013i5ngyp664gy	More thoughts!	f
cj48nxyxp00023i5n432opq2a	Even More Thoughts!	f
cj48o50eb00002y5oi2f4g8j1	Hi!	f
cj48p4dmu00022y5op6afdv7l	New Group	f
cj48p23we0001315omsdgdfp3	test group	f
cj48oen2i00012y5oahvwm7fw	https://zoom.us/j/820256284	t
cj48p4k5300032y5oq4ceb833	saddas	f
cj48ptfz700002y5ohwafz8uw	New Group	f
cj48pua5i00012y5op7yds2py	New Group	f
cj48swvum0000315oxnvy2c0m	New Group	f
cj48sxo6f0000315ouflqxjj1	hallo	f
cj48syjiy0001315oz6wxirqc	rnsrsr	t
cj48syh9b0000315oajolbu3v	sss	t
cj48sz3610002315orwzn0t1w	nsons	t
cj48t0hvw0004315o39ma1io7	ghkeh	t
cj48t0f270003315o3zihfb5m	ss	t
cj48t0s9e0005315obantxrlg	nsr	t
cj48t37ql0007315ocewdqdya	fgfm	f
cj48t415c0000315ov3jeedu1	sss	t
cj48t1mnu0006315ogv0lr5mt	hallo	t
cj48t7m210002315ov3k4ha15		t
cj48sxx600001315o8zfmph7a	Sansibar	f
cj48t77ax0001315o5jnz2ni0	Was machen wir dort?	f
cj48t9w2d0002315ol435k6m3	Safari	f
cj48ta8df0001315og6fcet3n	Nationalpark	f
cj48tfcj000003u69kc4b5ij5	Dschungelcamp	f
cj48th13a0003315ot679llkd	warum?	t
cj48tgi3300013u69k3bkpmdh	Dschungelcamp	t
cj48t6q1h0000315oi229beqz	Todo	t
cj48t9mue0001315okgwn8hb4	Tauchen	f
cj47jan6b00032y5o80bar215	code view	t
cj48tfv4u0002315o78t7dqnl	du bist ein witz	t
cj49z6fg600012z605xqf897j	dsa	t
cj48tnnqh0003315oi3287olo	Unterkunft	f
cj4a1bnvh00001w6599vwaht5	Foodie	f
cj48ts9sq0008315og1946hxu	Dar el salam	f
cj4a1e1gd00011w659y8xl19c	ToDo	f
cj48trdqh0004315o1xsgqre7	https://www.agoda.com/de-de/abc-travellers-hotel/hotel/all/dar-es-salaam-tz.html?checkin=2017-08-13&los=1&adults=3&rooms=1&cid=-134&searchrequestid=bdbfecf6-fe9a-49db-9c9d-046ad4b88f7b	f
cj48uswof0005315o4ot74lhp	ToDo	f
cj48ut8wa0006315ocmug9c2z	Visum: http://www.tanzania-gov.de/18-stories/dokumente/42-bestimmungen-zur-erlangung-eines-visums	f
cj48uujdf0009315owc6rcel5	Welche?	f
cj48uunls000a315ogoux3h3e	Wie lange?	f
cj48uuxbp000c315oc9ayk7gi	Online buchen?	f
cj48uv5hu000d315owboa5szm	Welche?	f
cj48uyg4d000h315obt74zhh0	Geld wechseln?	f
cj48uzbhy000i315okiye7gfy	Tjark 	f
cj48v0z7y000j315oxgmiq6ny	Wie lange sollten wir in Dar El Salam bleiben?	f
cj48v25wv000l315orr5qmu7p	In der nähe von Kilimanjaro	f
cj48v1kpu000k315ogrdvj2nk	Wanderung	f
cj48v5gt7000m315ocs0yrow6	Visum fÜr Sansibar?	f
cj48t9fzr0000315ox5cg9ehd	Kiten	f
cj48uveuj000e315ojp1dmhxk	Sansibar	f
cj48uvppf000f315ouu836vb1	Von wann bis wann?	f
cj48uw1p2000g315oz9wrk6oy	Boot buchen?	f
cj48uuqgt000b315ov2m8w7uw	Wann?	t
cj48v988i000n315oysixrejl	In meine Tasche....	f
cj48v9eia000o315oiae2jmtm	Gopro + zubehör	f
cj48vb89k000p315ot5q795mm	mini enceinte	f
cj48vbkr3000q315ownpo85nx	frisbee	f
cj48vcnaj000r315ojtqp9l21	Reisepass	f
cj48vqpfr000s315oy3i86xg2	wo auf Sansibar man wohnen sollen	f
cj48vqvqs000t315o7ofvrcc1	Auto mieten?	f
cj491077o00002y5ooyycxkmi	New Group	f
cj4a1el2o00021w65crdjv90c	Sinnvolle Extrempunkte	f
cj493huks00002y5ojgp40ibl	invite	t
cj493i5iz00012y5ob9id32gd	in invite by name flow, 	t
cj4956lke00002y5os69tes27	New Group	f
cj4956qqd00012y5ols4rys5x	New Group	f
cj4956sw200022y5ow7fpdq9c	New Group	f
cj4956x9o00002y5oscli1ywh	New Group	f
cj49573ld00002y5os35mo750	New Group	f
cj49585c800002y5oihfkckmn	redirect to root if current graph selection yields empty result	f
cj4958r1w00012y5o672cy9c3	New Group	f
cj49590si00022y5og4h0o6e4	New Group	f
cj4959d9r00032y5o7zivoxrh	asdf	f
cj495c75o00042y5oaazmr04m	New Group	f
cj495es7500052y5otmf6wcqp	asd	f
cj495evnd00001w5we8vrl4wz	asd	f
cj495gcnx00002y5oclpkb4qc	dsaads	f
cj495i3xd00001w5w5ith8b0e	asd	f
cj495il3o00011w5wbh3zvrvd	das	f
cj495qy8j00021w5wm2tp0759	does not know about own posts after reload	f
cj49ebyc700003r54enioonkz	New Group	f
cj49mcgq200002y5ok2bdw6wv	creating new group with implicit login, does replace graph and then getgraph	f
cj4a1gaqf00031w65vos3r8eg	Gewürze: FourirTranformation	f
cj49yplkn00002z62fsk1tvqc	chosen solution	f
cj49uvgav00052z63m987dkdf	sdadsa	t
cj49v12ei00062z63xufkn3me	dsadsa	t
cj49uvf5p00042z63utsgbwgv	adsa	t
cj49yzvsl00002z60uz46bpmt	test with enter	t
cj4a1hb5100041w65fedt6mwd	Komplexität und Zubereitungsdauer aus Länge bzw. Zeit der Linie	f
cj4a1ko6s00001w63c4iuis0w	hugöa	t
cj4a1n80800001w63ow00fpt5	Jo Boyz	t
cj49uinp100032z63gaitvoe6	keep sending changes in parallel, but have sequencenumber for changes so they can be reordered in case of failure	f
cj49uh6n300012z63ue72wg2d	possible solutions	f
cj49uhnek00022z63saylw2pe	only have one changeset in transit at a time and send changes sequentially	f
cj4abn1z900072y5o8rn3j4qf	mark selection out of multiple alternative solutions	t
cj4abibip00032y5od4vsxefj	posts are not respecting newlines	f
cj4abj1h200042y5ozy0oox6o	cannot uncontain post from currently focused post	f
cj4abg97z00012y5oj46r6wdm	reusable tags\n\n- we need to support the workflow of reusing certain post as containments.	f
cj4aboxfu000b2y5otl3e4103	is delete a containment?	f
cj4abm7nv00062y5oiev6u7fb	hashtags in text content to contain in other post #idea #problem #goal	f
cj4abnnax00092y5otq29kicy	mark type of post: idea/problem/goal/...	f
cj4abhk1a00022y5octlcop01	containment workflows	f
cj4abuu2d00032y5o8gf56rw1	visual containment as it is now	f
cj4abnd3g00082y5o5up5giyp	mark status of item: done/selected/wontfix...	f
cj4abtc6c00012y5o4rrodzqc	marks hierarchy of posts: on content-level	f
cj4abu12700022y5ocl9t1v80	tag (post-color/hashtag/badge)	f
cj4abwsem00042y5o6tcm4qyq	mention user: certain user/group should be notified	f
cj4abzgml00062y5oay5fv96n	global badges	f
cj4ac08te00072y5o4xsrexz1	bei private zusaetzlich public, wa?	f
cj4aboqc9000a2y5o17yu6fy8	question (badge me)	f
cj4ablk6300052y5od4ezqw64	idea (badge me)	f
cj4abzcu100052y5ojivudnca	project/post/group-specfic badges	f
cj4ac5wpk000b2y5otoo9w19r	crdt	f
cj4abx9fm00001w631ix0vpxk	Bei privaten gruppen eigene tags, bei public gesharte tags	f
cj4ac1kwh00082y5o7dsjch56	who decides what is a badge and what is containment?	f
cj4ac1wv100092y5o5z0za7ck	theree should be some agreement about certain posts	f
cj4ac2exl000a2y5ojr0qnrho	without autocompletion -  we are lost	f
cj4abaa5400002y5obrbtl10w	Issues	t
cj4abq2uf00002y5ofs94wkby	visualization	f
cj4ac685k000c2y5o6ymuytx3	when modeled in postgres	f
cj4ac702i000d2y5o7p554e8n	is a delete post an insert into rawpost with isdeleted true?	f
cj4bwc2nc00002y5owvb59y0v	search!	f
cj4bwqnx400002y5op7fjlbf6	isolated/uncontained posts should attract each other	f
cj46lepws00073k5n9suxptes	Article und Code-View ist Broken	t
cj49ubsak00002z639irmsher	ordering of graph changes can be lost, when succeeding dependent changes fail in client	f
cj4ac9zmq000e2y5oxc0vh7do	works, but more traffic/render than needed	f
cj4acessc00002y5oxaokuf5e	undo/redo should make visible what they did	f
cj4aci2ew00012y5oh5c5tqy3	shortcut button to go back to root graph selection	f
cj4b5wr4f00003k63yrk7i7nk	Externe Daten	f
cj4b5x1m600013k63soae0o2q	Gesis Mikro-Zensus 250 Euro pro Jahr	f
cj4b5x57y00023k63y3v0oawn	Vor-Ort Daten	f
cj4b8bw6y00002y5oweda5vrj	hans	f
cj4b8c7tj00012y5okof2e5wa	ads	f
cj4bb404r00002m5sejovbnka	I think so	f
cj4bcn7cz00002y5o1wf1hp6q	TODO	f
cj4bcnw3v00012y5o56nseyb0	discuss enrichChanges 	f
cj4bco0vi00022y5onsw1n194	explicit vs implicit	f
cj4bco66l00032y5okmx1vy08	when does it make sense	f
cj4bcobtn00042y5ocy2wcrp6	what does it do?	f
cj4bcosc700062y5oua6d9jwg	add containment for current selection	f
cj4cxt1ox0003225o6ct6dgcd	d	t
cj4cxsxx00002225odoucx7oe	c	t
cj4bcojkf00052y5olyr1jv7s	add ownership for current group [IMPORTANT]	f
cj4bcqn0p00002m5sjdnivj4y	wrong when adding a post inside another one	f
cj4bcukch00092y5ojhoatztl	only contains uncontained posts!	f
cj4bcxfga000c2y5oigpi388i	in general, unclear what direction of connection means. ordering in which direction?	f
cj4cxsqbz0000225o51p1e7pp	a	t
cj4bcwa13000b2y5o5cm2ixus	we look at the collapsable posts of a to-be-deleted postId. these are then also deleted. 	t
cj4bcysxb000e2y5ou7cqgdem	great, then these can go away :)	t
cj4bcw82w00032m5s56mngtfp	I think so	t
cj4bcuuwd000a2y5o73i16evi	      Collapse.getHiddenPosts(state.displayGraphWithoutParents.now.graph removePosts state.graphSelection.now.parentIds, Set(postId))	t
cj4bcvipe00022m5s5chb8r6c	No, Collapse.getHiddenPosts is basically transitive children without intersecting children	t
cj4bcycxe000d2y5o65xqhgo1	ok, then it is correct?	t
cj4bcrloa00012m5s5982m4nq	?	t
cj4bcqdha00082y5ovik6cr7p	also this is wrong, deletes based on collapsed status of other posts?!	t
cj4cxsw1o0001225o98hytphd	b	t
cj4bcpuet00072y5ojcdghkg9	delete transitive non-intersecting children of deleted posts	f
cj4bd1hl7000l2y5oh1a4b6z0	no. works!	t
cj4bd0ovs000h2y5o18kx7tu5	example	t
cj4bd0w2c000j2y5ob99tdkm2	B	t
cj4bd114n000k2y5o3dkb6o65	inner	t
cj4bd0tg8000i2y5or8gprm86	A	t
cj4bd01ux000f2y5o7ieh62zy	correctly handle deleting two posts that have intersecting children	t
cj4bd0jky000g2y5o212oye2l	currently, we handle each deleted post individually	t
cj4ee6by200001w63lxrk03e0	Einkaufen	f
cj4bd26d3000m2y5o5xmtmzs0	this correct!	f
cj4btfj7o00002y5o9vb2y5le	schreib mal solved	t
cj4btv5ji00002y5ojlumqtra	felix took it away :/	f
cj4ee6rhf00011w63kwxbljno	Gurken	f
cj4ee6v6w00021w63mp2ls0sh	Tomaten	f
cj4ee6xil00031w63ppmat88r	Feta	f
cj4ee6zol00041w63mgbb2iuo	Oliven	f
cj4ee735t00051w63o4zv5r78	Zitrone	f
cj4ee7kvo00061w63sp5y5yqh	Bauernsalat	f
cj4ei5sip00012z5nlgj5cogv	Nachtisch	f
cj4ei6ab600052z5neiltxzn5	Eis?	f
cj4eia3dj00062z5ny8h676xb	Moving cards in BoardView crashes in Firefox	f
cj4eicsq200072z5ne2p3k17v	Getrocknete Tomaten	f
cj4eid7nd00003i63e3xjxp7e	ui	f
cj4eiedgw00023i633lhkj1lr	Petersilie	f
cj4eifbon00033i637y9ste9b	Rote Zwiebeln	f
cj4eihnad00053i63l7j82r1m	Brot mit Tzaziki?	f
cj4eiki1v000c2z5nxn7ahzmv	Oder einfach noch mehr solche Pasten vom Kaufland	f
cj4eig81a00092z5n09h9d0y1	Ich habe gerade keine Geduld Brot zu backen	t
cj4eiex0h00082z5ni9a9wrco	Alternative?	t
cj4eijx5e000b2z5nkh6gsiib	geht auch	t
cj4eihyml00063i63v3ui547w	also gekauftes Brot	t
cj4eih2xq00043i63ku92v4x3	Süßkartoffel-Pommes?	t
cj4eijgd9000a2z5n1tbpu5hj	im Ofen?	t
cj4ejvck500073i6300js8sjq	auch nicht schlecht!!	t
cj4ei5plm00002z5nup8jrxud	Süßkartoffelbrei	t
cj4ei662t00042z5n6pnhkhg3	Gemüsebrühe	t
cj4ei5wpx00022z5nwk3cv0ed	Süßkartoffeln	t
cj4ei5zxh00032z5njbquweq8	Thaicurry	t
cj4eie34x00013i63slr8raom	Passt Süßkartoffelbrei zu Bauernsalat?	t
cj4fovg8r00002366dzhwwyd2	ja	f
cj4fpqdm50000316349ur4fsz	When deleting, notify that the post was also deleted to other members of the group and provide a big undo button.	f
cj4fr74xv000031635ug6yv4q	clicking outside invite-window should close it	f
cj4fr7hp600013163cxw8cub4	ESC should close all dialogs	f
cj4ftbz270000295pk5f3n4hw	ja	f
425	https://onlineservices-servicesenligne.cic.gc.ca/eta/welcome?lang=en&_ga=1.185572010.203288567.1458244437	f
cj4qssxa000003c64szhnu3zr	Awesome!	f
cj4qst5yi00013c64rt7lrhf3	Mathematik	f
cj4qstdup00023c64yta1gkx1	Ist doof	f
cj4qstiss00033c64pae8def0	Schule	f
cj4qsu4h900043c644bvtu97g	ist cool	f
cj4qsudao00053c64rfswbhq5	macht spaß	f
cj4qt8ipk00003c64e7791iqh	Crazy	f
cj4qtbrwe00013c64sdy7ma2n	nein	f
cj4qtbz0y00023c64nua2sdmt	test	f
cj4ymfzeu0000315ozejbabjt	HCI	f
cj4ymg4fj0002315o6f3xdgll	Work	f
cj4ymg2a40001315o2ud3zckr	Travel	f
cj4ymhhw60005315ok1ckizf2	B	t
cj5fqrybk00003i54faiiuhmf	oh	f
cj4ymh5cl0003315ocn5yk4x9	Riding a Bike	t
cj4ymhiqn0006315o1cl8ki6l	Conference	f
cj4ymhh4a0004315onbm9xmwk	How should I go to work?	f
cj4ymhlt20008315onhbk9oli	Canada	f
cj4ymhkw80007315o1loxkein	X	t
cj5hcq9rk0002315oo6nlrw04	Wurst	f
cj5hcqfqq0003315oz24sfenp	jo	t
cj4ymjm3o0009315oqrc5w7yn	By Bike!	f
cj4yml8ps000b315o9lvnskhm	Montreal	f
cj4ymkxoo000a315oljvg9ix1	Vancouver	f
cj51emfpk00003s4rn3f9mom1	Demo	f
cj51emoyx00013s4rmpshe0lw	Animal testing	f
cj51en4tz00023s4r6osriocd	could be useful	f
cj51enlcs00033s4rpjvme9jv	could be useful	f
cj51enupi00043s4r0fbddgin	is ok	f
cj51er3sj00053s4r90uvgql0	is not ok	f
cj3x3pi4900013k5k9gjeuaiy	Test	t
cj5fqira800003q681spcpcgc	New Group	f
cj5fqj3j000013q68oqy0dkui	Fdsfvg	f
cj5fqk1x500023q68zb7uqnx4	Thanks\n	f
cj5fqqi8f00003i62yg5t2vp4	New Group	f
cj5fqs0ti00013i5401q15yqk	cool	f
cj5fqu5nj00023i62ao5invw8	Hello	f
cj5fqu8g100002z5ra2tvdm8z	Hi there	f
cj5fquirz00033i62d7pcxgwb	How's it going?	f
cj5fqv70k00012z5rek9nme1r	How do they say it in Canada?	f
cj5fqvwf100043i62ve0x7mt5	test	f
cj5fqzocs00022z5rfidhsg0k	What's that all aboat?	f
cj5hcr16s0004315oek3phqhd	noch was	t
cj5hcswg40005315ovvq5720l	r	f
cj4zbdmnt00001y5j2l4o2mrk	aha	t
cj5fqtmos00013i6276f7z76c	talk	f
cj5fqz8x600003i62ni5a6uff	They say 'eh' in Canada	f
cj5hcq13m0000315ohkm0bvfz	Luka	f
cj5hcq7o40001315ozs3k66uw	Hallo	f
cj5khaxje0000315oj5r7ywe6	Ottawa	f
cj5kigrvr0001315o7fr1lpq1	Hallo Felix!	f
cj5kjqm2r0000315oaneej5pl	hide invite window when clicking outside	f
cj5l70d4a00002y5qye5fpk31	good	f
cj5ln04uy0000315nzf1cn0ic	Hallo Felix und HannesJo	f
cj5ln0him0000315oewok5r55	Felix, du Wurst!	f
cj5ln0q9p0001315nnd99jjet	Ich habe den Pfeil kaputt gemacht	f
cj5ln0us10001315o8rhrljy7	ist nicht schlimm	f
cj5ln3fm10002315ng00j9aa8	Cool	f
cj5ln3ov00003315nsmq93zoc	Wie passiert das jetzt in dem Graphf?	f
cj5ln421z0004315n5oi8j2cf	ah ok habe ich verstanden	f
cj5ln4l2g0005315n2w7nb9pm	hier ist einfach alles nur nach einander	f
cj5ln4vep0008315n23c99kj1	?	t
cj5ln4vy40009315nfah6eju4	?	t
cj5ln4wjj000a315nfn3uwlzv	?	t
cj5ln4xaq000b315n9wlt2h3r	?	t
cj5ln4xwt000c315n18hbnpzc	?	t
cj5ln4s200007315niccd3stx	wie viele kann ich da machen?	t
cj5ln502p000e315nxu368613	?	t
cj5ln50ok000f315nyw8bngxi	?	t
cj5ln5pck000g315nk8rcbc91	it's a snake!	t
cj5ln4mvh0006315ndoyrvbi4	ja	t
cj5ln4zgm000d315njnnnf7rs	?	t
cj5ln737z000h315nglqx4bhs	echt?	f
cj5ln75ol000i315nldtoohgw	ok	f
cj5ln79a5000j315nn59exi8b	nicht schlimm	f
cj5lpsgvk00002c5he0mgppjq	ellie	f
cj5lpv1yv0002315o5qpewjf7	Hi Ellie!	f
cj5lpwcvm0000315ogbedui71	who?	f
cj5lpwtba0001315oknkkj4mn	What do we do this weekend?	f
cj5lpxiqw00022c5huhke1kdc	Wir gehen klettern am samstag juhu	f
cj5lpxla40002315oijhq4pln	Wrong	f
cj5lpycee0000315oo2qoxcsv	hagen dugu	f
cj5lpyzmh0001315o2pzpmlss	me!	f
cj5lpxo5w00032c5h6z172gsa	fuck you	t
cj5lpvcyj00012c5hhct4v34f	HALLO	t
cj5lq07o10002315oy6olanzq	Right	f
cj5lq0fr400042c5h3v2z6jo3	Warum	f
cj5lq30vi0004315oipx62m6e	Google	f
cj5lq33w60005315ome3xfh3d	Facebook	f
cj5lq38jq0006315oh3ihbpb3	Things with OO	f
cj5mseowc000k3c6hpn5xw69g		t
cj5lq2y0m0003315ow6s9qi5t	Roo.me	f
cj5lq3iu600052c5hibx3p2hm	boob	f
cj5lq68nx0007315on3o48io7	Cool	f
cj5lq6qca00062c5h4hkg8etg	things with ee	t
cj5lq744g0008315oxfaw6d5p	Peenis	f
cj5mso1qr000l3c6hee3uu9lo	task	f
cj5lq6yxp00072c5hx7h0z9nv	long things with ee	f
cj5lq7hgb00082c5hj9sborqi	teesticules	f
cj5lq7pmn0009315ozslgokmr	Vageena	f
cj5mrijzd00003c6hic6doada	S032	f
cj5mriyox00013c6hv1okkwq5	Material ressources	f
cj5mrkqu700073c6hjtpuirgj	test 1	t
cj5mrklvr00063c6hfm9n017u	Test	t
cj5mrjqpj00043c6hkw5b9cul	table	t
cj5mrj2io00023c6hwbkq3uwh	Task	t
cj5mrli4v00083c6hksy58iu7	table	t
cj5mrkatj00053c6hbbl3g3ni	Workforce	t
cj5mrj8g800033c6hljoz4sx1	HR ressources	t
cj5mrq0xk000a3c6htxftpfj8	Task	f
cj5mrq7fz000b3c6hver24fdv	Benoit	t
cj5mrqnce000d3c6hbnsj6t3d	Setup of room S023	f
cj5mrqvza000e3c6h9cpibdvm	Table #1	f
cj5mrqzrq000f3c6h1pnst8y9	Table #2	f
cj5mrr1vy000g3c6hs4sq2ujs	Table #3	f
cj5msqpuk000m3c6h4dsaa4yq	something	f
cj5ms4jy5000h3c6h9xbrl18h	Felix	f
cj5mrqepz000c3c6h23rppjgw	Benoit	f
cj5ms4sst000i3c6h9bzklnm1	Take Felix out	f
cj5msqu64000n3c6hqm7fdyv8	problem	f
cj5mrpwq700093c6hxpvj2ayt	Person test	f
cj5msej7o000j3c6h4yt4y0v1		t
cj5mszqu2000o3c6h2acqpgiv	test	f
cj5mt1217000p3c6h4ckx79l8	S032A	t
cj5mt1j1e000q3c6hpn09570n	S032B	f
cj5mt1qbu000r3c6hntgduoms	Task	f
cj5mt1t9u000s3c6hfqrrq6ed	Material	f
cj5mt1ui2000t3c6ho58167oe	People	f
cj5mt28q2000u3c6hgyaeyyyd	Task 1	f
cj5mt2iwa000v3c6h9qut0wk0	task 1.1	f
cj5mt66pl000w3c6hok8i5tvy	test	f
cj5mt677l000x3c6hfcozkwtg	sad	f
cj5mt67qh000y3c6h8e11bify	fa c	f
cj5mt688h000z3c6h46zz033q	dfgd	f
cj5mt68qz00103c6hxy53pqt7	fgd	f
cj5mt69ol00113c6hl6j0g765	asda	f
cj5mt69vs00123c6h4lai6kb3	sdf	f
cj5mt6aca00133c6h074fjkmk	dasf	f
cj5mt6apy00143c6huhpb3eq9	asd	f
cj5mt6b5p00153c6h9mzs38ko	asd	f
cj5mt6bjp00163c6hot1vq7nk	fg	f
cj5mt7xui00173c6hhbudartc	Benoit	f
cj5va7j8m00003x68yyy2ac6k	Test Group	f
cj64zz7pd0000205wkxyiuayy	Pa Malte	f
cj6500zl40001205wzrkuxb8t	Getriebe	f
cj65015af0002205wmawhhrre	Glasbehälter	f
cj65018kn0003205wvrri9bp9	Temperierung	f
cj6501adz0004205w0tbzp0ti	Licht	f
cj6501cof0005205w7qrs5rue	Kamera	f
cj6501i670006205wocooweap	Gerüst	f
cj6505dhp0008205wqdnqcfua	Welle-Narbe Verbindung vs Madenschraube	f
cj65089ip0009205wkf4octzj	Passstifte vs Formschluss	f
cj650an0p000a205w4uai5ks1	Auswahl des Standardsystems	f
cj6503f470007205w7f6y0y5d	Abweichungen bei bestehender Zeichnug und realem Getriebe	f
cj656ys5b0000305ooz7aap5a	New Project	f
cj656zgyf0001305os8fet4oo	Wie organisieren wir unser Projekt?	f
cj6570rr80002305oi5cvxn9o	ToDo	f
cj6570t0s0003305olj9b1m90	Doing	f
cj6570ty40004305ohqqbvusl	Done	f
cj6570kvy0001305odt82xky6	Wir benötigen folgende Spalten:	t
cj656zwhv0000305ojjzxgn87	Kanban-Board	f
cj6577m7z0005305o8zmu918x	Technologie Evaluation	f
cj6577ozb0006305oyww7wpca	Gründung	f
cj659wims0000305om9r195mo	Meine Firma	f
cj659wm9j0001305o880cus8v	Hallo	f
cj6xbz6l300003i5ox3x7ihm5	SmartIdentification	f
cj6xbzicb00013i5oncmer98a	Terminplanung	f
cj6xbzy4300023i5otg53p4id	Volle Konsortialtreffen	f
cj6xc0a2200033i5o7093li32	DE-Treffen	f
cj6xc0cmy00043i5o0wj6mpnu	Bilaterale Treffen	f
cj6xc4b9k00013i5ohea1ci3l	Gruppen löschen?	f
cj77hwm5v00023x66x810d2bk	Was geht?	f
cj78s50cs00092y609ffvgzby	Use tags for view control.\nFor example, the "ui:state" tag could be used in the board view to control which columns are displayed on the right side (e.g. TODO, WAITING, DONE, etc.)\nThe "ui:group" tag on the other side, could be used to show only one group on the left side of the board view with a drop-down to quickly switch to other groups.\nThis would allow to quickly create TODO entries inside a group (e.g. "My Research Project") and then move/copy them into the respective state (i.e. TODO, WAITING, DONE, etc.)	f
cj6xc2mie00003i5ojigob5zf	Im Spam-Kneul kann man über den "One-Level-Up" Button in eine Sackgasse laufen.	f
cj6xc8c9g00023i5o0c26uex2	CoE IoP	f
cj6xc8yde00033i5oxprp2zbo	Mensch-Roboter Kollaboration	f
cj6xc92tm00043i5om3avvto5	Mensch-Algorithmus-Kollaboration	f
cj6xc993t00053i5ooqvj7j79	Visualisierungen	f
cj6xc9cr500063i5oylqpee6u	Ergonomie	f
cj6xc9e4x00073i5owuf3o74s	Akzeptanz	f
cj6xcan9s00083i5ob2vf6b51	Unstructured Problems	f
cj6xcau0g00093i5o5vbigv9b	Human Competencies	f
cj6xcbkwf000a3i5om4ru1qye	Problems	f
cj6xcc8t3000b3i5oxhvm08w9	Structural Complexity	f
cj6xccic6000c3i5otycddiwp	Large Amounts of Data	f
cj6xccupy000f3i5oqshz5evi	User	f
cj6xccz9q000g3i5o9uisr5el	Machine Data 1	f
cj6xcdsvh000h3i5o53ybqwei	Machine Data 2	f
cj6xcdv3p000i3i5o9e4bdqh4	Machine Data 3	f
cj6xcdx4t000j3i5o7bq2epov	Machine Data 4	f
cj6xcf9ln000l3i5olneln26d	User 2	f
cj6xcfmlu000m3i5ooimy102n	Competitors	f
cj6xccm1a000d3i5o36pyeu6k	Platform data ?	f
cj6xcixl0000n3i5oakr5mg46	For nicer screenshots. Hide "x" on edges until hovered..	f
cj6xccse6000e3i5oukwqzgrr	Owner A	f
cj6xce911000k3i5oi8ea1lrq	Owner B	f
cj72edi1c00003260ug8h45av	Looking goood	t
cj72eh38o00013260smm1ljkw	Pretty Nice	f
cj72eh5f900023260fa9q2eic	Lol	f
cj72ehcqx00033260k3t5q0ub	Laugh out loud	f
cj77hpvv200003s66wopm8i84	thomas	f
cj77hqm7m00013s664p0f6aw5	Mittag essen	t
cj77hrwuy00023s663kyvv0bg	Status bit für XXX löst nicht aus	f
cj77hrm7700003x66s1ha7r0w	Hallo	f
cj77htg6700033s66xdcf7h9e	Könnte an falschen Daten liegen	f
cj77i5i6300073s66rkjeg0dc	Festplatte ist wohl voll. Habe alte Daten gelöscht	f
cj78rzzik00082y60d61718p1	Board view should allow to add tags to an entry, instead of only allowing to move an item between tags.	f
cj790mnvk00002z62na6itf1r	Graph export.\nIch würde woost ja häufiger nutzen, habe aber angst um meine Daten, falls ihr das DB-Schema mal ändert :D	f
cj7984hlm00082z5oclcbenon	foo	t
cj7g7sj3u00002y5o0be5zkyf	Ähnliche Ansätze	f
cj7jb6a6600012y5p1p5slruj	ich	f
cj7jb77dn00062y5pmogoi2tu	du	f
cj7jb79lz00072y5pffmuewa9	dies	f
cj7jbc4c600022y5p2gv7x5r3	neue column	t
cj77ht9q100013x66g1b8ysec	Warum?	f
cj77hw5x000043s66sj8c9mfo	Verbindung nicht hergestellt?	f
cj77hxxy700053s66roampaaf	Habe heute Logdaten ausgewertet. Die sind ok	f
cj78rqh2j00022y607s3n5mx4	DONE	f
cj78rr71x00052y60twri8ubu	Research nix tf pkg	f
cj78rrguy00062y60au4eklob	Test self-built tf 1.3.0	f
cj77i1c4i00063s66g5stoga6	Logdaten vom Vortag fehlerhaft und nicht Auswertbar	f
cj78rq2jd00002y60gngh7nqg	thomas	f
cj78rqf8c00012y60edwsjw69	TODO	f
cj78rqmzy00032y60vvzw6k84	WAITING	f
cj78rqusw00042y60rhxwctqn	Build TF	f
cj78ru5bd00072y60wt82tfxl	Tensorflow	f
cj7jb80um000a2y5p1cp2hmic	anderes	f
cj7981p4q00012z5o4y5769dx	programmiersprachen	f
cj7981r4k00032z5o1g1jnfq1	scala	f
cj7981qoa00022z5oae4ey4x9	f#	f
cj7983dkf00052z5otv0sekl5	deutsch	f
cj7983fr300062z5ofi0rrli1	englisch	f
cj79812kb00002z5ol979v48q	meine sammlung von dingen	f
cj7983b7a00042z5okzh1dmbi	natuerlichen sprachen 	f
cj798423c00072z5oromtc800		t
cj79865o700092z5onlnprda7	chinesisch	f
cj79868wh000a2z5obddizjdc	spanisch	f
cj7986gh700001w5lnyxmhtdx	hollaendisch	f
cj7g7sn1100012y5o8nsyt0lp	IdeaFlow	f
cj7g7soyj00022y5osxmt32jl	Nuclino	f
cj7g7srec00032y5owpfn7pwm	Zenkit	f
cj7hqm32600003i60do32gwik	scala.org	f
cj7jb5zkr00002y5pxmy2jk07	my group	f
cj7jb6hgl00022y5py44bncem	fussball kaufen	f
cj7jb6m9t00032y5plnkpcrbe	pc reparieren	f
cj7jb6oki00042y5piwfqhzoq	obst essen	f
cj7jb6qwq00052y5pbis60q7a	bier trinken	f
cj7jb7ckm00082y5pijvfcoon	jenes	f
cj7jb7dvi00092y5p83rjwmg3	das	f
cj7jbap4400002y5pvtonbq63	asd	t
cj7jbac4b00002y5pg2c7z6s3	foo	t
cj7jb9yp5000b2y5pnn3ltev4	niocht	t
cj7jbbypz00012y5pe81xzjvr	uifehiwq	f
cj7jccj0500002y5p19vtsm5x	fdg	f
cj7jcckq900012y5pafuf6gyp	sadf	f
cj3pzm79d00043k5km2l2pwc7	das	t
cj3pzmbc700083k5k59aahdsp	nicht	t
75	Achso	t
cj7lyw4fb00003r4onhw9ebi7	Dg	t
cj7omn2b3000021624mx5kotc	sdfsh	t
cj8vwjrh20000315pfxsyv1ce	achim	f
cj7p5zo2100001w5lb8rus0x1	test 	f
cj7p5zpr900011w5livok13hi		t
cj7q0qloh0000855dcasqochv	Hallo2	t
cj7q0qac70000305p7zql154d	Hallo	t
cj7q0rjeg0001305p8nd6mdfz	Ordner	t
cj7q0ri2y0001855d9yjamf7j	Meine Füße sind kalt	t
cj7q0rqqs0002305por0l7nl1	Probleme	t
cj7q0sedt0002855dndnrxquv	Socken anziehen	t
cj7rcmbr20000855dc1f44ha3	Hallo	t
cj7q0sk5p0003855dn0rxkkcf	Idee	t
cj7q0mac70000305pfziyd5nl		f
cj7rcpru50001855d1ojqrvcn	Kind erziehen	f
cj7rcpv2o0002855dyh5jr56r	Herausforderung	f
cj7rcpkfg0000305psm4y5ewf	Was ist dein Problem?	f
cj7rcqa840003855dcq4st85l	Ziele	f
cj7rcqhw60004855dy984uejt	Selbständigkeit entwickeln	f
cj7rcr7ik0005855dywyb72wo	Dinge selber machen lassen	f
cj7rcrcix0006855do03obgb6	Vorschlag	f
cj7rcri9z0007855du1k8ij38	Kind kann sich verletzen	f
cj7rcroyb0008855d96ts07z7	Schwierigkeit	f
cj7rcsj480009855dyl13tlt9	Ich weiß nicht wie ich die Erziehung angehen soll	f
cj7rctd9c000a855dcuvjbu69	Kind will nur Nudeln essen	f
cj80jywja00003f5pqy4tm3e9	de	f
cj7rcs4g90000305pszfimtpz	Viel unterschiedliches Essen probieren lassen	f
cj7t2vebr0000305pzgfvkg3s	gossi	f
cj7t2x7gt00002a5l04samucs	hello	f
cj7t2xcb100012a5lvfduj1rd	wuast	f
cj80jzj9g00013f5p86n7bo0f	New Group	f
cj7t2x7p00001305p1j6vkvko	Hallo	f
cj7t2xbzy0002305p5n8s1ryg	wie gehts dir?	f
cj7t2xvk80003305phryrt14l	test	f
cj7t2zxw000022a5llzstufvb	ja super	f
cj7t324xv00032a5l5kjmms8z	bombe rakete	f
cj7t32jz100042a5lkx8jnqq9	reintun	f
cj3su9bqa00003i60o3s8ri2m	Tree-view posts, werdeen nicht umgebrochen und ragen in die Icons rein.\n	f
cj3ya8iep000a2z639foa1ym9	input focus lost when update comes inee	f
cj80jwc9h00003f5po77buurk	fdf	f
cj82uwj9y00002y61wve4dp3v	test	f
cj82ux9hg00032y6198bd1hd9	Muss noch spühlen	f
cj82uxp1c00042y618qkn5ueh	Muss noch spühlen	f
cj8h7gt0p00002c5umobzdytc	Immo 360	f
cj82uwpm600022y61ol0qmhrm	DONE:UIElement	f
cj82uzhjj00052y61nw70e6fc	UIElement	f
cj82v153f00062y617fwayh9x	In progress	f
cj8h7h8dm00012c5uqrwq25cw	Immo 360	t
cj82uwn3900012y619yvotgcs	TODO	f
cj87fj44m0000315p9etud75d	Mars Colonisation	f
cj87fjdn50001315pwstp2u3t	State	f
cj87fjgsn0002315pwx3revru	To-Do	f
cj87fjk380003315p3amzr770	In Progress	f
cj87fjl330004315pmxy9sax4	Done	f
cj87fjoko0005315p1bi49s4x	Importance	f
cj87fjrsf0006315pq50vnksl	Medium	f
cj87fjsp30007315p1t2dgz17	Low	f
cj87fjthd0008315p3t9lnyll	High	f
cj87fka9h000a315p3zp8sl52	Establish mars colony	f
cj87fkg63000b315phrfxq7q7	Fuel tanks	f
cj87fklil000c315pdk4vxtzm	Choose flight menu	f
cj87fkqkg000d315p1q7u255o	Find a name for colony	f
cj87fkvfu000e315pe2djpz45	Choose cool color for ship	f
cj87fkz6d000f315pyz44rd50	Test hibernation boxes	f
cj87fl2s2000g315pxmwa21si	Build spaceship	f
cj87fl4uu000h315p3mi4muhg	Find crew	f
cj87fwv0m000i315p90qxzx24	Task	t
cj87fk4ob0009315p1ppmck76	Task	f
cj8bv5juz00002w67m06jy22m	Hallo	f
cj8h7np0t0000315ppnpqpuz4	Jo	f
cj8h7o7fu0001315puvh4mhz0	Was verkauft ihr?	f
cj8h7obim00032c5ujwpzlplt	Ok, erzählt mal was zu eurem konzept	f
cj8h7olm200042c5u88ntk4go	Wir verkaufen einen Scanner	f
cj8h7oqr400052c5uezvxh2ed	Verkauft ihr auch schon was?	f
cj8h7ou9u0002315p6p7p6ncs	nein	f
cj8h7plfg0003315pgb4pheax	Wir entwickeln noch	f
cj8h7q25y00062c5ud9oxtdzw	Ah ok, und habt ihr schon mit dem EXIST Antrag angefangen?	f
cj8h7q8y90004315pci3j3zae	Auf welche Arten könnt ihr euer Produkt verkaufen?	f
cj8h7qg670005315pxvqmo3vi	ja, wir sind bei ca 90%	f
cj8h7r3r60006315pxjrhx1jt	Woost	f
cj8h7r89q0007315pieou2lvq	Immo 360	f
cj8h7rfo500072c5uacv19ptf	Noch nicht ganz sicher. Einerseits wollen wir einen Hardware Prototypen bauen und verkaufen, aber in erster Linie wollen wir uns aufs Hosting konzentrieren	f
cj8h7szs800082c5uieu190js	Hi	f
cj8vwjva60001315p8fiegqa1	hallo	f
cj8h7nfjf00022c5u3grvar0w		t
cj8h82er400002r5ugcav6ccj	Was ist ein scanner?	f
cj5nu9nn70000315s4hd1rfjc	Tiphanie war da	t
cj8vwjw040002315pe8at0xtf	bla	f
cj8vwkct90004315pwj2cdmsb	iuae	f
cj8xin72w00042r5ufotlakfx	pbit	f
cj8xingdt00052r5ukfwme1ma	enutrine #todo	f
cj8xiodg100062r5u5ogueyuq	eurtn	f
cja9irqi100013j5utt9hcjc2	hi	f
cja9iufx500043j5uoe2dl32y	we should plan our journey to paris	f
cj8vwjx630003315p5u4f3izy	haha	f
cj8vwkikt0005315ptvn8zy35	aeae	f
cj8xivkl600092r5u2sge3raj	eunridt	f
cj8vwxdsi0006315pb1bjmzre	app	f
cj8vwxg600007315pjkb36lke	auelele	f
cj8wrdub600003d65v5wxnngd	Achim_Test	f
cj8wre70800013d65pjd35gk1	Achim	f
cj8wre9u200023d65f1gi79lo	Achim sagt hallo	f
cj8wrebyr00033d65dh0oy16j	Neues Thema	f
cj8wrekeo00043d65brwbtjfk	Neue Aufgabe	f
cj8wrez8h00053d65ffk0nrxg	Zuordnung zu neuer Aufgabe	f
cj8wri6b200063d65bd7t8m2i	Michael	f
cj8xivrbl000b2r5u1mqeh4m5	1600	f
cj8xixg6x000d2r5uu32kjkla	Eckdaten	f
cj8ximo1n00002r5uujivoe4j	testify	f
cj8xims9g00012r5utnupg1jo	ue	f
cj8ximw8i00032r5uycym97hy	momj.	f
cj8xin54900022r5uq00ksjb3	nertuin	f
cj8xin64z00032r5uothwc5mo	ehgx	f
cj8ximtqx00022r5ukb4dxt9l	sio	f
cj8xin3vq00002r5uws9ibris	netrun	f
cj8xin4n500012r5ulqvtfi0d	nedturn	f
cja9issv700033j5ursvj84h1	hi	f
cja9iuscn00003j5u5iqih7sp	who is talking	f
cja9iv7gx00053j5udx4gx060	use different colors for different users in chat view	f
cj8xitk5n00072r5uyt2cuvj4	woost	f
cj8xivhdk00082r5uwnk39bkx	urlaub 2018	f
cj8xivoca000a2r5umy9f58dg	1800	f
cj8xivzkt000c2r5ulu83rtdq	03.01.2017	f
cj8xj7o4400012r5ur1osaxkt	eeg	f
cj8xj7s9s00022r5uihrsuqtu	personal	f
cj8xj82sl00062r5ujbivr1e6	empfang	f
cja9ufeqs000a3u5ut7cqdiga	no time for that sry	f
cja9uft4d000c3u5ucre3hbg1	eating	f
cj8xj1s2q000e2r5ujlu2e9m3	unet	f
cj8xj6yu800002r5uw6pvw5x9	medical	f
cj8xj7uk700032r5ub91qekmo	ärzte	f
cj8xj7zcv00042r5usa47inhp	psychologen	f
cj8xj80yg00052r5utgctaefm	labor	f
cj8xj8dvw00072r5umbenjl4a	patient	f
cj8xj8jhz00082r5umizp9x73	daten	f
cj8xj9j1f00092r5uug3ro32e	warteliste	f
cja9iql3l00003j5umcnm6bh1	test	f
cja9iscx200023j5u1i5a2rgn	planning discussion	t
cja9isvk400003j5uc6x0o1hz	hey	f
cja9it7yz00003j5utd1y7mf9	hey, whats up?	f
cja9iut6j00013j5uspcwfshj	?	f
cja9uao4600003u5uupch92me	Planning committee	f
cja9uau5100013u5u098c1rbi	hey	f
cja9ubxmj00023u5uajhxqr0n	Can plan our journey to paris?	f
cja9ucpba00033u5ug2xxa9e2	Yes, do you have a plan?	f
cja9uctvr00043u5uadwajcjo	flying or driving?	f
cja9uegqx00073u5uzkl16bbv	I would suggest traveling by car and split\nup gas costs. That cheaper than flying or\ntraveling by train.	f
cja9uf7y700093u5ub6afyvrg	je prefere voler a paris :-)	f
cja9uglan000e3u5u1px3ibpc	Holiday	f
cja9ugfzf000d3u5ui6izmd3m	Eating	f
cja9udyxw00053u5u01ax8r1s	Are you hungry? let's eat something and talk about it	f
cja9ueyqk00083u5ugrx3myeb	should we go anyway RED?	f
cja9uflo8000b3u5urxo3yma8	no time for what?	f
cja9ue7at00063u5u26ldqtkh	yes, what about GREEN?	f
cjac91bly00003h5p0ufh7kcr	find ich super	f
cjac9322i00013h5pjedid51b	Agilität ist das Konzept, das Herausforderungen iterativ bewältigte.	f
cjaf352jy00003b5xc0dn1po8	How are you?	f
cjaf35b8s00013b5xui1g4pie	shiny	f
cjaf35j6100023b5xtehdh1na	really?	f
cjaf37km600033b5xn4bfon3r	New Group	f
cjaf38aim00043b5xyy5vugz8		f
cjaf38ye900053b5xnoldgmiw	as well	f
cjak1nokt0000315s9lnp1urc	i3-easyfocus\n\nFocus and select windows in i3.\n\nDraws a small label ('a'-'z') on top of each visible container, which can be selected by pressing the corresponding key on the keyboard (cancel with ESC). By default, only windows on the current workspace are labelled.\n\nUsage\n\nFocus the selected window:\n\n./i3-easyfocus\nIt also possible to only print out the con_id of the selected window and, for example, move it to workspace 3:\n\n./i3-easyfocus -i | xargs -I {} i3-msg [con_id={}] move workspace 3\nOr to print the window id and use it with other commands, like xkill:\n\n./i3-easyfocus -w | xargs xkill -id\nConfiguration\n\nUsage: i3-easyfocus <options>\n -i    print con id, does not change focus\n -w    print window id, does not change focus\n -a    label visible windows on all outputs\n -c    label visible windows within current container\n -r    rapid mode, keep on running until Escape is pressed\nYou can change the keybindings and the font in src/config.h.	f
cjak1ofiy0001315su6w0ot22	Usage: i3-easyfocus <options>\n -i    print con id, does not change focus\n -w    print window id, does not change focus\n -a    label visible windows on all outputs\n -c    label visible windows within current container\n -r    rapid mode, keep on running until Escape is pressed\nYou can change the keybindings and the font in src/config.h.	f
cjak1omwk0002315s1f6nhl3c	Usage: i3-easyfocus <options>\n -i    print con id, does not change focus\n -w    print window id, does not change focus\n -a    label visible windows on all outputs\n -c    label visible windows within current container\n -r    rapid mode, keep on running until Escape is pressed\nYou can change the keybindings and the font in src/config.h.	f
cjak1ov3z0003315s3kouy2d5	Usage: i3-easyfocus <options>\n -i    print con id, does not change focus\n -w    print window id, does not change focus\n -a    label visible windows on all outputs\n -c    label visible windows within current container\n -r    rapid mode, keep on running until Escape is pressed\nYou can change the keybindings and the font in src/config.h.	f
cjak2o0mg00001w5ko42geegq	asd	t
cjak2o1ge00011w5kq67tmtdo	ad	t
cjak2o4re00021w5kaij5oejb	ads	t
cjak2o5gp00031w5kfc5njcyg	asd	t
cjaseooe500002y5qa088q3t5	DNA Evolutions	f
cjaser7o500002z5nqdxjlz41	Herzlich Willkommen, Jens!	f
cjaserstq00012y5qra1hb0kx	Heyho	f
cjaseq5id00003b61k86ee0bc	Selber	f
cjaseso2f00002z5n5m328ckj	da!	f
cjasesq2a00002y5q1r3l8rn3	joooo	f
cjaseu0ye00012y5qqi4k9cop	hier	f
cjasexn5200022y5q6gfety4b	ha	f
cjasexpi400032y5q395fb6r6	ho	f
cjasex1h300033b61tomgyp2f	asd2	t
cjasew1l600023b61kjn6k7a8	asd	t
cjaseqs5e00013b6173kqoszr	Test	t
cjay1iv7r00002z5p8shuxb80	test	f
cjay1iwii00012z5pjjk5nx8a	test	f
cjay1ixbi00022z5pn1qp3xsv	test	f
cjay1iy5m00032z5p5fb5h65r	tes	f
cjay1iyy600042z5p1222ywij	t	f
\.


--
-- Data for Name: schema_version; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY schema_version (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1	graph	SQL	V1__graph.sql	422010744	wust	2017-02-12 21:16:48.167033	598	t
2	2	user	SQL	V2__user.sql	410358264	wust	2017-02-12 21:16:49.15561	323	t
3	3	incidence index	SQL	V3__incidence_index.sql	1300472430	wust	2017-02-28 15:34:02.761139	354	t
4	4	user revision	SQL	V4__user_revision.sql	-2009960029	wust	2017-04-04 14:48:09.789856	580	t
5	5	user implicit	SQL	V5__user_implicit.sql	-449324864	wust	2017-04-04 14:48:10.4345	232	t
6	6	user ownership	SQL	V6__user_ownership.sql	-2029942174	wust	2017-04-19 16:39:32.801532	733	t
7	6.1	put old posts into public group	SQL	V6.1__put_old_posts_into_public_group.sql	66940450	wust	2017-04-19 16:39:33.590682	82	t
8	6.2	give groupless users a personal group	SQL	V6.2__give_groupless_users_a_personal_group.sql	-492950262	wust	2017-04-19 16:39:33.724206	57	t
9	7	invite token	SQL	V7__invite_token.sql	884677346	wust	2017-05-02 13:47:37.231461	330	t
10	8	rename usergroupmember usergroupinvite connects contains	SQL	V8__rename_usergroupmember_usergroupinvite_connects_contains.sql	128024823	wust	2017-05-02 13:47:37.620099	30	t
11	9	membership userid set not null	SQL	V9__membership_userid_set_not_null.sql	503128850	wust	2017-05-10 18:33:48.997412	177	t
12	10	eliminate atoms and views	SQL	V10__eliminate_atoms_and_views.sql	-1930295764	wust	2017-06-09 13:51:02.843211	1343	t
13	11	forbid self loops	SQL	V11__forbid_self_loops.sql	-1891569201	wust	2017-06-09 13:51:04.273497	109	t
14	12	post uuid	SQL	V12__post_uuid.sql	-559146598	wust	2017-06-09 13:51:04.496385	814	t
15	13	rawpost	SQL	V13__rawpost.sql	-897484842	wust	2017-06-09 13:53:50.244311	307	t
\.


--
-- Data for Name: user; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY "user" (id, name, revision, isimplicit) FROM stdin;
1	hans	0	f
30	a	0	f
31	hannes	0	f
32		0	f
33	dsf	0	f
34	anon-86ee33e9-7d3c-4222-b963-bccf3f467e6e	0	t
35	anon-9404b1e8-1726-48f6-b4b6-79f7a014c366	0	t
36	anon-d9949749-9dc6-40f9-bdb8-4efef2fa8e87	0	t
37	anon-992d119b-a9b0-405c-881a-98e67a02c244	0	t
38	anon-e04051a3-451b-46cb-8ecf-8e2cb61af303	0	t
39	anon-f1117843-2962-4e9a-89ef-5106df279174	0	t
40	anon-ab9daa53-26fd-49a3-826a-c905489d19ec	0	t
41	anon-fb9a5eca-fc92-4b43-8d99-432a71ec6a97	0	t
42	foo	0	f
43	anon-90550b58-5d24-492b-b7d5-c33ab4b6ded9	0	t
44	anon-b0c5742c-fc03-4757-adf5-584c0685f728	0	t
45	anon-3031fa25-8148-4eee-a738-b150c86ff2c1	0	t
46	anon-c60970e1-cca4-45c9-b815-e1cd4dd51600	0	t
47	anon-ac9a91e3-9679-4465-869f-ff66da675e17	0	t
48	anon-1fb7b6de-d570-4d7c-b395-c36aefd07132	0	t
49	anon-3baceff9-a82f-4b3f-9c94-509e61cd66af	0	t
50	anon-24834a38-3491-498b-ac97-42dd7d26737b	0	t
51	anon-048a877a-f1d3-487f-86da-ed8e374e60b1	0	t
52	anon-480bf561-356f-4070-9c7c-a912839b5b7c	0	t
53	anon-05aac7ee-876f-45e2-86c9-e7be34588276	0	t
54	anon-28667f8a-1cdc-4478-aa70-e2aa9647e923	0	t
55	anon-68f111f4-948e-4cd6-9a57-33b1bbead98b	0	t
56	anon-ed1b35f2-04cf-4990-b29f-c15153b173da	0	t
57	anon-cce0fcb2-d7dd-46ce-80e3-49d1609346bb	0	t
58	felix	1	f
59	Sumidu	1	f
60	anon-ae00b346-9e37-4a15-9165-b95c25993c7f	0	t
61	johannes	0	f
63	dsdasdasa	0	f
62	sadasd	1	f
64	asddas	1	f
65	anon-7b2a1114-a81a-4a00-87cf-648612fdfb23	0	t
66	anon-6286845f-c0d5-4605-a02b-13b82e6b8020	0	t
67	anon-95e4ae06-7c19-4ff3-aaf3-a3d1cfd05785	0	t
68	anon-146cf0c7-d91f-4618-badb-a0dfcbe7b4b4	0	t
69	anon-4cbdcbb9-4d15-4395-84c4-9da07d5930b5	0	t
70	anon-a48b3a61-1c81-471c-9a5c-0a871b525349	0	t
71	anon-7a5a7f69-4fb4-4c80-af49-de66e7ec9f0d	0	t
72	anon-7e90ff26-5e9f-4dce-9bc5-bbf773791d32	0	t
73	anon-2edbd809-d83a-4772-828c-6a78b4759218	0	t
74	anon-ce6592bf-7884-4f21-8b99-6a8708398caf	0	t
75	anon-67d31ad9-df9c-416a-8871-7fc9a0cb4dd0	0	t
76	anon-4394be11-ec49-492a-b8a6-1a0c3821aa9a	0	t
77	anon-8b309ab1-4577-4313-8b9a-58ecbc2a394b	0	t
78	544334543	0	f
79	anon-b79911da-b475-456b-948f-7a7b75a0a0a8	0	t
80	anon-8f4c989c-24af-4afe-a7cb-aa069eb8e6bc	0	t
81	anon-8c37944c-541e-46b0-b252-6c1dfcd7bbaa	0	t
82	anon-9479e386-e531-4d94-9f4e-85fe47b830e1	0	t
83	anon-459d8f3c-7678-4011-9b18-183d47569996	0	t
84	anon-1fec7a82-ea4e-4c90-b619-6e27b052165a	0	t
85	anon-00d6c19b-d6f5-4342-a393-feba112a1a55	0	t
86	anon-fdbc934f-19db-4d82-b700-812544ffeffb	0	t
87	anon-8fdd3849-a123-4e6f-a7de-a7469b05709e	0	t
88	anon-26bbedec-36ed-47fb-8515-29d52c286e11	0	t
89	anon-c9674c8d-7237-4cee-8722-071421f979a3	0	t
90	andré	0	f
91	anon-3c7d3b91-e1cb-4155-92a3-314bc4a429c3	0	t
92	anon-e1d13b76-42a5-4824-bb63-9971b06b7c0f	0	t
93	anon-5fc78ec2-d4af-4b7c-b889-46ed6a3bafce	0	t
94	anon-97793d34-f3d4-4b5b-9ed5-9c8afa75103b	0	t
95	bla	1	f
96	anon-a67ce092-868b-4e40-82e6-16181bf9069f	0	t
97	anon-baa7b7c8-84f2-49b5-9952-685f128d36c9	0	t
98	anon-f493a70c-807f-433b-8c55-806f374ec5ca	0	t
99	anon-7967664c-83ea-4edd-a6b3-71fb8b5e42c4	0	t
100	anon-95da1405-31e8-4080-9d30-7444e3def8b4	0	t
101	anon-b78b8324-cee3-450f-9457-8ef2cb444524	0	t
102	anon-46102073-9083-48d6-bfbf-04a8037156a2	0	t
103	anon-1e892e69-ea57-483c-817e-a99fb76307a2	0	t
104	anon-215f3f3f-b7f9-42da-b14c-fffede6d95bc	0	t
105	anon-7cc81012-85bd-412a-82a3-a345720fb132	0	t
106	anon-a53c4821-c58f-41fd-b70a-603f50cba1da	0	t
107	anon-97c989d1-213d-44a4-8069-e32acd8c4ec2	0	t
108	anon-a49c35ae-0761-4332-8f06-4b13331f3ddf	0	t
109	anon-9fb291ba-ae40-444d-a092-11353a86b1bc	0	t
110	anon-9ffe8603-0a21-4fba-a79e-3306a56daffb	0	t
111	ACaleroValdez	0	f
112	anon-12656123-a881-4200-944e-f3b1c85c4bed	0	t
113	anon-2fec3612-4362-450c-bedc-09911ccfcf4b	0	t
114	anon-4586a37a-ab69-4f43-a60c-d2598fb48e28	0	t
115	anon-7b910612-58eb-49c2-a62e-3ff311e7bb2c	0	t
116	anon-269d505b-1585-4362-8e49-4dc9bee79735	0	t
117	anon-845ca586-928d-43be-b7f9-9165c37ec442	0	t
118	anon-954b9adc-f3b5-4d41-90c4-238be8077745	0	t
119	anon-1a317667-3be7-4176-a35b-dd823e6ba430	0	t
120	anon-a49259ca-8de3-4656-92af-8a1402de9450	0	t
121	anon-222dcb12-61b6-4a5a-a205-d5fb736de957	0	t
122	anon-0d10926c-1c52-4203-b085-fbc903e99ea0	0	t
123	anon-25e948d3-94fa-4a4d-aaff-383ae350e3d1	0	t
124	anon-1a2453cc-4522-480a-a84f-2f85cdd31d75	0	t
125	anon-6130936d-696b-4f38-a58f-2f6b188b6bbd	0	t
126	anon-14c0e3ba-0cd1-4984-869b-8389b8ef4a39	0	t
127	anon-50b9d96b-524c-44be-b3f5-acac5fc80586	0	t
128	anon-cf090811-72d1-4e2d-b8d0-22a64c5dc252	0	t
129	anon-d28272a1-cf4a-41e7-91b8-d59662447d77	0	t
130	anon-62ccc25a-4689-4c1c-8938-e87f6275cda7	0	t
131	anon-9b06f302-50b4-4c07-9124-78fc900b314e	0	t
132	anon-81d508b1-9566-48fe-96bf-4f89e384c8e1	0	t
133	anon-e9f3fb5c-1d8d-4d1c-b870-f52235726f78	0	t
134	anon-f31a126b-70f0-4ac7-b778-7b532d8a48c3	0	t
135	anon-cef09c55-e707-4f5f-895a-16337e74dd65	0	t
136	anon-489e9233-4ce2-455d-9bb7-e75b6855f803	0	t
137	anon-1c4149c5-0187-4fdd-acd6-413bf06f7c74	0	t
138	anon-b715b0db-62eb-492e-8c54-7af63ee0969e	0	t
139	anon-439036a3-5e30-4545-a4ee-20c89f50477f	0	t
140	anon-fb35a4a2-559e-4f18-8705-4c1a16f11246	0	t
141	anon-524c22d6-9b23-4429-adea-2951a9093d3f	0	t
142	anon-3f5f3134-715c-4b9d-970e-aa286f25d467	0	t
143	anon-e2e8797b-bd7d-4860-989b-a1eae00d0680	0	t
144	anon-b10b9c84-15d7-4b56-bf02-7073af8851e1	0	t
145	anon-8b7ed842-c05a-4f33-a22d-78c4924ef02f	0	t
146	anon-eb5161d2-fe61-419c-863f-894beff1b469	0	t
147	anon-10598385-be8a-4e8b-8105-3fe8fa4600c0	0	t
148	anon-795ce2d8-0da1-40c7-b67c-aab4025f3768	0	t
149	anon-e2fe31c5-e3d5-41e8-8722-5301cab99f5e	0	t
150	Julius	1	f
151	anon-182c4d5f-74e6-4614-8230-abe3a6048f55	0	t
152	anon-be04aea4-9ac6-4043-b21e-f223b4a715b0	0	t
153	anon-8aad6e26-4707-4ea8-be00-1352b3d9801d	0	t
154	anon-89ec3dc9-4e00-4044-b1c3-8d56e9bf7cd9	0	t
155	anon-9b89af25-c740-48d9-8305-29b3d680f726	0	t
156	anon-c80ced8d-1e0c-4919-a586-70c0769a5a2b	0	t
157	anon-6e6a584e-82d6-4830-bd6c-1a2845308821	0	t
158	anon-c0136c4a-5801-49c4-a300-e441de67f776	0	t
159	anon-156416dd-411b-4d51-8bcf-ae0b38d75773	0	t
160	anon-aec5a9a0-e3b8-4da6-85e5-0d5e3c7d8958	0	t
161	anon-6bb4a82a-12e6-4189-beb4-d5e3bebd49e9	0	t
162	anon-24b2b765-05d7-44db-83e1-34a60b863cb5	0	t
163	anon-d0566603-f5d0-4b6e-ae82-b49ff4c1a647	0	t
164	anon-8de940a4-8651-4e42-acd4-1c53e07090f1	0	t
165	anon-1afe8ab1-4491-4565-8faf-f31bd2402501	0	t
166	anon-e6a489cf-f3f2-490a-afb4-93f05c6c8e0f	0	t
167	anon-0336ec35-94fc-4994-b2f3-b9d8c04626e2	0	t
168	anon-bac7841e-5fdf-4236-9fec-c3009dc074c1	0	t
169	anon-113bf2f3-da3c-4774-9340-60e59dec6757	0	t
171	anon-16e7756f-6d9b-4e1d-8fe0-d3c1f6f2b505	0	t
172	anon-9e5eee3c-e28d-4595-adb0-b829a5dc3d9d	0	t
173	anon-29dd620d-c2b3-489b-a287-db51a6ab6ddb	0	t
175	anon-7a8c634e-9034-4e91-9269-15ec46935adb	0	t
176	anon-0832f0f6-b1c4-4069-b1e1-ca2e5d138af9	0	t
177	anon-156e6ad6-61d6-4e27-bfe3-7b5af5f0dd16	0	t
178	anon-058b02f0-82f8-442c-b8e8-11acaec7b8aa	0	t
179	anon-bb73b4d2-b17b-402e-bf90-8661306f9509	0	t
180	anon-61a67d88-9f2a-44d5-b27d-415927bc3c8c	0	t
181	anon-c2b46b5f-faf2-4773-b781-fbde19a74da5	0	t
182	anon-a99ab826-d0e5-47c9-9666-2d2f62696e49	0	t
183	anon-a2ee864d-a499-49ed-8eb7-45faa1247df4	0	t
184	anon-e8804a3b-f607-4087-a9bb-7ea3e3338c95	0	t
185	anon-66293f06-35ca-4ce7-a9f4-4ff97feb8b3f	0	t
189	anon-f8d74542-7ea0-4ef7-bece-40615bcf5933	0	t
190	anon-11804d66-d00b-4546-83ad-e962d8aa9edc	0	t
191	anon-a90c8bb4-adca-41a1-a3d3-909ec8505cad	0	t
192	anon-a6d4194b-4a8a-4331-86c4-cd1189823e46	0	t
193	anon-c2e69606-557b-4997-bef4-3fd042343f78	0	t
194	anon-d3deff82-63a2-4e17-9f55-fcb1af4c905a	0	t
195	anon-7d370c1d-cf5d-4f41-9b1f-e936d747b2f3	0	t
197	Conor	0	f
198	anon-afab133f-8aa9-4dfb-8f68-34e37debec35	0	t
199	anon-c43eadcd-c7ea-4306-8eec-9bc730d2e5f1	0	t
200	anon-9d2af34c-09bd-4a3a-b44f-0d1d1051631b	0	t
202	anon-ab365952-6ef7-4c72-aa12-258e20307896	0	t
203	anon-97eb3205-e25d-43b1-b172-6f553c743f7f	0	t
205	anon-26c48700-0739-4f08-982c-ed21ee6ced6c	0	t
204	dsaasd	1	f
206	anon-2b71da22-1601-48a2-8ccd-2a1eae1664b2	0	t
207	anon-7ad6be96-4bd5-4c6d-9e59-fc9c33a7c09b	0	t
208	anon-69f8664a-5c76-410b-98b7-ee9d957cf6c5	0	t
209	anon-9128d451-2d7c-48e3-8c63-7564c5375557	0	t
210	anon-a3449b55-3027-4dcc-b4fb-6ec1ea50294a	0	t
211	anon-ecc58dbd-0c7b-4c17-a260-bf3f56bff5c3	0	t
174	lipflip	1	f
212	anon-028c3073-2986-4cdd-8ce4-6a4ba0e61008	0	t
213	anon-4faf13ce-885a-445e-9232-74465779232e	0	t
214	anon-a06ecbc5-e70c-45b4-ab82-50acc2054ed3	0	t
215	anon-2b014c8f-6e09-4a5c-8748-27fb4f1a2852	0	t
216	anon-ad5cb13a-505c-4f18-8ac5-14d1b4a8c1b7	0	t
217	anon-df6de8f4-5e79-44ba-a87a-f64b817a1c13	0	t
220	anon-13cf6e26-e6e9-4b29-bc5b-53a017c363e2	0	t
221	anon-34617e95-580f-45d5-b5ae-36f1bd8a62ee	0	t
222	anon-3038db79-e527-40de-ad12-b3598d13eab0	0	t
223	anon-e8fbc0ea-4c5d-48d0-9766-36cc244b2cf2	0	t
224	anon-91ad32f1-10b5-4609-bdbc-80a70c295c84	0	t
225	anon-3cfea167-b9cc-48c7-9229-cad5291bf903	0	t
226	anon-bd1424a5-d9a1-4ae8-8fa9-c01c717abd4b	0	t
227	anon-b0235e3b-bd22-40b2-abff-414231da4a8e	0	t
228	anon-7182181c-1c64-4f8d-989e-12dc7ff48c0f	0	t
229	anon-f7cd4890-1c3c-4142-8cd5-309b5025d3cb	0	t
230	anon-7d3c82ad-054b-40f3-ba1c-460486b6fc62	0	t
231	anon-097f9127-cc40-4626-8b43-2459d6773758	0	t
233	anon-b4204812-39dd-4eb7-9283-bb2bd4bb0b5e	0	t
234	asd	1	f
235	anon-bc16c0bf-ea5b-4614-a697-6f5a5da08605	0	t
236	anon-0b7978d7-e91b-4772-80f6-d58a0598aff8	0	t
237	anon-bb262f3e-cd2d-4b91-9316-505f2093c594	0	t
238	anon-b89f74df-d386-4392-9d24-9f07e4a6e654	0	t
239	anon-41a1b1e8-977f-4e82-b773-f631e3aaac06	0	t
240	anon-1ed85837-9ea1-4fd8-a51f-a888db267aa6	0	t
241	anon-6fd0617e-ea94-4c6c-a2f4-c34cf893ca43	0	t
242	tjark	1	f
245	anon-391b1fef-84bc-4266-839d-1a1f5ff1d1e5	0	t
246	anon-642d52fd-ddff-4877-8053-e473c645616d	0	t
247	anon-ffce0031-25f4-41a9-a72a-23eb005d5276	0	t
249	anon-1e67d6e1-e817-423e-9c3d-fd11e2c60a54	0	t
250	anon-42d134c0-2270-482d-9569-60f0e5c45286	0	t
251	anon-3a95ccbb-f34e-4765-9665-169411e2a1dc	0	t
252	anon-529e48a1-8aa2-40b8-8811-e039026a3db5	0	t
253	anon-820b5c67-3598-488e-87a0-82593321d7de	0	t
254	anon-f46bb224-b3f5-4f14-a891-9db6d250b6d0	0	t
255	anon-0aa63405-7531-4fbc-917a-196e604c5c30	0	t
256	anon-2d45e573-7409-40c6-b4b4-ede5b0d1b28a	0	t
257	anon-6fb50274-bece-4d01-b343-410fa280f877	0	t
258	anon-d0d171b8-569a-4c1b-9875-4987ac7fdf37	0	t
259	anon-7166a93f-5f40-4bc8-964d-b4818b8382cc	0	t
260	anon-9eb08291-0abf-45ef-8f20-35f1e4862c54	0	t
263	anon-b638ec63-2e1e-4fb4-9658-07e0a02792b5	0	t
264	anon-8263b26b-1857-4487-8c3b-06f5aa5484ca	0	t
265	anon-e16c07b1-b7a8-4b75-a72f-1c8748bf387a	0	t
266	anon-85eb342a-1b50-4d93-b7d0-686561fdbca0	0	t
267	anon-386ccbaa-f3c7-4f3e-ae61-306f0a87bc0e	0	t
268	anon-d76aac51-fee6-4c40-8ee3-6c72510ea741	0	t
269	anon-ead210e7-5dc5-4778-90e3-554c739eee73	0	t
270	anon-1e5d8ea6-7eae-4bcb-9786-998e3c416d1f	0	t
271	anon-c826d4be-eb51-4995-a0c5-ac3fc3bffb02	0	t
272	anon-e00e00b3-a7ca-4848-9909-4552588cdb7e	0	t
273	anon-94295672-a265-45ca-a59b-ab0dc42ca7dd	0	t
274	anon-99b428ba-39e8-445b-af86-0c3b2d92dadb	0	t
275	anon-0a9f4faf-b841-45fc-878b-4c0650573f6c	0	t
276	anon-1d7908c4-e7a1-42cf-b3ec-b53f480e90ea	0	t
277	anon-c94f2f16-c0f1-4808-b938-0187b7e10fc2	0	t
278	anon-0f916d7e-be38-451b-9854-a1de3a4d0f04	0	t
279	anon-c248ed82-4179-4e54-b0d2-fd5032da14d1	0	t
280	anon-48c1e5e8-d5fa-4ac2-aaa0-7bb846defc24	0	t
281	anon-9a3f9f30-1568-4b44-9303-d4deec054878	0	t
283	leex	1	f
284	anon-ffedeb3b-5d80-4886-93f7-072b9b2006b5	0	t
285	anon-b9ba3233-740c-4623-ba0e-ef2352fc6e86	0	t
286	anon-3b361e7c-23ab-4787-a0cd-be87f554541e	0	t
287	anon-781139a3-a4aa-4b91-a1cf-b4a942248a03	0	t
289	anon-579cfc76-1bed-4960-8970-68a71030cc25	0	t
290	anon-d87e2308-5bd3-4ee2-bbf9-d66ccf46aff0	0	t
291	anon-e06985d2-3627-4372-9049-0834bcc269d7	0	t
292	anon-dcc75fc5-80d2-4700-8f49-2249b2e94849	0	t
293	anon-644bf04b-7843-4a71-a7e6-6b61d64bf5f1	0	t
294	anon-783d2e61-e48c-4f54-9254-ba0f34d78060	0	t
295	anon-9d0f21c5-01e1-4218-bded-461191cedcf3	0	t
296	anon-2c3dcbe0-78d7-4291-b86c-ff8a7a5234b1	0	t
297	anon-3d64f520-2713-4e01-b6f4-2ad13ae19814	0	t
298	anon-23efac7a-8494-41f9-9823-3afd5c40fe7b	0	t
299	anon-dc5f4ede-3fb7-4f4e-90f0-44dc63c838a7	0	t
300	anon-dd897f68-f283-4f61-8ee2-1fa94d817585	0	t
301	anon-475aaf8b-4d66-4aa6-b3d5-ee8578b2caa6	0	t
302	anon-f61b9f90-69f6-4c98-b266-6f4ea9936331	0	t
303	anon-11d9a0f1-0972-4f6a-9c04-85eb87fa8deb	0	t
304	anon-b108ef3a-ff89-4b1b-936b-6f6c885b80f2	0	t
305	anon-8bd23742-d1de-4c19-82c4-78abb8533ecc	0	t
306	anon-d45209d1-b915-4e47-bdfc-abdb908caabe	0	t
307	anon-ebaf08eb-7ade-4b80-a114-73e13e411192	0	t
308	anon-8975686d-389d-441b-91e8-6f7cb2c6a30b	0	t
309	anon-4e4e5171-4bc3-4bd0-b926-5c25734f62f4	0	t
310	anon-7450e38c-f836-4a5e-a1e8-e63e89b57248	0	t
311	anon-1a43264f-2c7b-4e92-b6f2-01cee79b73bb	0	t
312	anon-1523102a-e7ef-456f-ae10-e1c15f53d10f	0	t
313	anon-a954979f-dc7c-40b9-a838-035e975c0b90	0	t
314	anon-7561f8c0-bcf4-4247-b83a-891c31792686	0	t
315	anon-67783b31-6819-42ed-910d-884446f073c4	0	t
317	anon-c193115d-780d-42bd-907c-7f154dadb3df	0	t
318	anon-6030f6a8-fedf-4ca9-b1f8-154600a63706	0	t
319	Hannes N	1	f
320	anon-69034d49-ab59-4362-a182-e1f169a61fed	0	t
321	anon-58a5c28d-dbc7-4ef3-9c6c-2c3f7306f1ee	0	t
322	anon-32631b5e-225e-4c60-afaa-f58a2099661d	0	t
324	anon-e87ea07a-0248-4d91-b281-1aedd2e0ba55	0	t
325	anon-0e4dacbc-1317-4136-8e3e-55ec47ff7e79	0	t
326	lars	1	f
327	anon-8e8753b4-a314-475c-bbdb-9367d00f1763	0	t
328	anon-efed5050-0ac6-4668-85af-cb4e5e674528	0	t
329	anon-4dc83b66-60ce-4dd6-82a8-195cac3f6f5a	0	t
330	anon-d1cfa581-2170-47ac-bc7d-da0d22e48287	0	t
331	anon-edc9f5bd-1154-432c-a396-a07228bbb429	0	t
332	anon-2550c734-de6f-4d92-a071-4bf8d6d76cd3	0	t
333	anon-d50651dc-d34a-4aaf-bab7-93bed8b7a09c	0	t
334	anon-4445b5a1-1028-48ca-99c8-b7b8d9959c47	0	t
335	anon-3468e8ba-3eeb-4b69-8c6d-4527267db5a5	0	t
336	anon-23a38ebd-d0ee-457e-83a9-eb92f4e51fa6	0	t
337	anon-266aa7cb-ffa4-4727-bdf4-1c5f1950a0e1	0	t
338	anon-d0bea042-db16-4af6-a6a8-e3744a4f2587	0	t
339	anon-cc95662f-94ea-4bed-b053-e06f1fdc81a5	0	t
340	anon-60cbbfec-4c28-4586-8a01-3dfa14083110	0	t
341	anon-1217cbd8-a7cd-4d93-93f1-5c6f2b5a20fc	0	t
342	anon-8f8d78a0-3d58-4b85-a9e9-f4e620546e48	0	t
343	anon-c9cd8372-b1d7-43f4-ba81-7fcf58e086f9	0	t
344	anon-74ba05cc-9fc0-4518-9af6-876f26550356	0	t
345	anon-d5bafb8d-a3e6-47de-9632-7745c534c940	0	t
349	anon-471ec0cb-dba6-42e3-817b-c0c6dfee44be	0	t
353	anon-8b22311e-e1b5-48bd-a90b-95fad14f47c6	0	t
346	anon-43d6dd91-6890-4c38-a8f6-688e7d9d702c	0	t
347	anon-6a57f6ad-6574-4a56-9501-3e0678257211	0	t
348	anon-1ecd63ec-4ec3-429c-9dfe-306b2f000281	0	t
350	anon-32f3c406-6040-4b3a-a35e-6283d21cf9ab	0	t
351	anon-9f80fec7-7f95-4ffa-900d-4b5eca5240c4	0	t
352	anon-1cb9bef1-36ae-4aba-821f-6a762c4ceff1	0	t
\.


--
-- Name: user_id_seq; Type: SEQUENCE SET; Schema: public; Owner: wust
--

SELECT pg_catalog.setval('user_id_seq', 353, true);


--
-- Data for Name: usergroup; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY usergroup (id) FROM stdin;
2
3
4
5
6
7
8
9
10
11
12
13
14
15
16
17
18
19
20
21
22
23
24
25
26
27
28
29
30
31
32
33
34
35
36
37
38
39
40
41
42
43
44
45
46
47
48
49
50
51
52
53
54
55
56
57
58
59
60
61
62
63
64
65
66
67
68
69
70
71
72
73
74
75
76
77
78
79
80
81
82
83
84
85
86
87
88
89
90
91
92
93
94
95
96
97
98
99
100
101
102
103
104
105
106
107
108
109
110
111
112
113
114
115
116
117
118
119
120
121
122
123
124
125
126
127
128
129
130
131
132
133
134
135
136
137
138
139
140
141
142
143
144
145
146
147
148
149
150
151
152
153
154
155
156
157
158
159
160
161
162
163
164
165
166
167
168
169
170
171
172
173
174
175
176
177
178
179
180
181
182
183
184
185
186
187
188
189
190
191
192
193
194
195
196
197
198
199
200
201
202
203
204
205
206
207
208
209
210
211
212
213
214
215
216
217
218
219
220
221
222
223
224
225
226
227
228
229
230
231
232
233
234
235
236
237
238
239
240
241
242
243
244
245
246
247
248
249
250
251
252
253
254
255
256
257
258
259
260
261
262
263
\.


--
-- Name: usergroup_id_seq; Type: SEQUENCE SET; Schema: public; Owner: wust
--

SELECT pg_catalog.setval('usergroup_id_seq', 263, true);


--
-- Name: connection connection_pkey; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY connection
    ADD CONSTRAINT connection_pkey PRIMARY KEY (sourceid, targetid);


--
-- Name: containment containment_pkey; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY containment
    ADD CONSTRAINT containment_pkey PRIMARY KEY (parentid, childid);


--
-- Name: ownership ownership_postid_groupid_key; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY ownership
    ADD CONSTRAINT ownership_postid_groupid_key UNIQUE (postid, groupid);


--
-- Name: password password_pkey; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY password
    ADD CONSTRAINT password_pkey PRIMARY KEY (id);


--
-- Name: rawpost post_pkey; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY rawpost
    ADD CONSTRAINT post_pkey PRIMARY KEY (id);


--
-- Name: schema_version schema_version_pk; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY schema_version
    ADD CONSTRAINT schema_version_pk PRIMARY KEY (installed_rank);


--
-- Name: user user_name_key; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY "user"
    ADD CONSTRAINT user_name_key UNIQUE (name);


--
-- Name: user user_pkey; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY "user"
    ADD CONSTRAINT user_pkey PRIMARY KEY (id);


--
-- Name: usergroup usergroup_pkey; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY usergroup
    ADD CONSTRAINT usergroup_pkey PRIMARY KEY (id);


--
-- Name: groupinvite usergroupinvite_pkey; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY groupinvite
    ADD CONSTRAINT usergroupinvite_pkey PRIMARY KEY (groupid);


--
-- Name: groupinvite usergroupinvite_token_key; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY groupinvite
    ADD CONSTRAINT usergroupinvite_token_key UNIQUE (token);


--
-- Name: membership usergroupmember_groupid_userid_key; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY membership
    ADD CONSTRAINT usergroupmember_groupid_userid_key UNIQUE (groupid, userid);


--
-- Name: ownership_groupid_idx; Type: INDEX; Schema: public; Owner: wust
--

CREATE INDEX ownership_groupid_idx ON ownership USING btree (groupid);


--
-- Name: ownership_postid_idx; Type: INDEX; Schema: public; Owner: wust
--

CREATE INDEX ownership_postid_idx ON ownership USING btree (postid);


--
-- Name: rawpost_isdeleted_idx; Type: INDEX; Schema: public; Owner: wust
--

CREATE INDEX rawpost_isdeleted_idx ON rawpost USING btree (isdeleted);


--
-- Name: schema_version_s_idx; Type: INDEX; Schema: public; Owner: wust
--

CREATE INDEX schema_version_s_idx ON schema_version USING btree (success);


--
-- Name: usergroupinvite_token_idx; Type: INDEX; Schema: public; Owner: wust
--

CREATE INDEX usergroupinvite_token_idx ON groupinvite USING btree (token);


--
-- Name: usergroupmember_groupid_idx; Type: INDEX; Schema: public; Owner: wust
--

CREATE INDEX usergroupmember_groupid_idx ON membership USING btree (groupid);


--
-- Name: usergroupmember_userid_idx; Type: INDEX; Schema: public; Owner: wust
--

CREATE INDEX usergroupmember_userid_idx ON membership USING btree (userid);


--
-- Name: connection connection_sourceid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY connection
    ADD CONSTRAINT connection_sourceid_fkey FOREIGN KEY (sourceid) REFERENCES rawpost(id) ON DELETE CASCADE;


--
-- Name: connection connection_targetid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY connection
    ADD CONSTRAINT connection_targetid_fkey FOREIGN KEY (targetid) REFERENCES rawpost(id) ON DELETE CASCADE;


--
-- Name: containment containment_childid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY containment
    ADD CONSTRAINT containment_childid_fkey FOREIGN KEY (childid) REFERENCES rawpost(id) ON DELETE CASCADE;


--
-- Name: containment containment_parentid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY containment
    ADD CONSTRAINT containment_parentid_fkey FOREIGN KEY (parentid) REFERENCES rawpost(id) ON DELETE CASCADE;


--
-- Name: ownership ownership_groupid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY ownership
    ADD CONSTRAINT ownership_groupid_fkey FOREIGN KEY (groupid) REFERENCES usergroup(id) ON DELETE CASCADE;


--
-- Name: ownership ownership_postid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY ownership
    ADD CONSTRAINT ownership_postid_fkey FOREIGN KEY (postid) REFERENCES rawpost(id) ON DELETE CASCADE;


--
-- Name: password password_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY password
    ADD CONSTRAINT password_id_fkey FOREIGN KEY (id) REFERENCES "user"(id) ON DELETE CASCADE;


--
-- Name: groupinvite usergroupinvite_groupid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY groupinvite
    ADD CONSTRAINT usergroupinvite_groupid_fkey FOREIGN KEY (groupid) REFERENCES usergroup(id) ON DELETE CASCADE;


--
-- Name: membership usergroupmember_groupid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY membership
    ADD CONSTRAINT usergroupmember_groupid_fkey FOREIGN KEY (groupid) REFERENCES usergroup(id) ON DELETE CASCADE;


--
-- Name: membership usergroupmember_userid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY membership
    ADD CONSTRAINT usergroupmember_userid_fkey FOREIGN KEY (userid) REFERENCES "user"(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

