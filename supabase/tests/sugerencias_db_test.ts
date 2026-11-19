import { PGlite } from "npm:@electric-sql/pglite@0.3.9";
import { strict as assert } from "node:assert";

Deno.test("sugerencias privadas, reserva concurrente, reutilización y borrado del estudiante", async () => {
  const db = new PGlite();
  const child = crypto.randomUUID();
  try {
    await db.exec(`create role anon; create role authenticated; create role service_role bypassrls;
      create schema auth; create table auth.users(id uuid primary key);
      create function auth.uid() returns uuid language sql as $$ select null::uuid $$;`);
    const schema = await Deno.readTextFile(new URL("../migrations/20260930160000_kmat_schema.sql", import.meta.url));
    await db.exec(schema.replace("create extension if not exists pgcrypto;", ""));
    await db.exec(await Deno.readTextFile(new URL("../migrations/20261113003000_sugerencias_docente.sql", import.meta.url)));
    await db.exec(await Deno.readTextFile(new URL("../migrations/20261114001000_sugerencias_dentro_app.sql", import.meta.url)));
    await db.query("insert into public.estudiante(id_estudiante,nombre_mostrado,codigo_nivel_educativo) values ($1,'A','BASICO_1')", [child]);
    await db.exec("set role authenticated");
    await assert.rejects(() => db.query("select * from public.sugerencia_refuerzo_ia"));
    await assert.rejects(() => db.query("select public.reservar_sugerencia_ia($1,'GEOMETRIA',5)", [child]));
    await db.exec("reset role; set role service_role");
    const reserve = async (n: number) => (await db.query<{ data: { status: string; lease?: string } }>(
      "select public.reservar_sugerencia_ia($1,'GEOMETRIA',$2) as data", [child, n])).rows[0].data;
    assert.equal((await reserve(4)).status, "insufficient");
    const first = await reserve(5);
    assert.equal(first.status, "reserved");
    assert.equal((await reserve(5)).status, "busy");
    const finish = async (lease: string) => (await db.query<{ ok: boolean }>(
      "select public.finalizar_sugerencia_ia($1,'GEOMETRIA',$2,5,'Figuras','2 correctas y 3 incorrectas','Asignar ejercicios disponibles de Figuras desde la app y revisar sus resultados.') as ok", [child, lease])).rows[0].ok;
    await assert.rejects(() => db.query("select public.finalizar_sugerencia_ia($1,'GEOMETRIA',$2,5,'Figuras','2 correctas','Usar tapitas de botella')", [child, first.lease]));
    assert.equal(await finish(crypto.randomUUID()), false);
    assert.equal(await finish(first.lease!), true);
    assert.equal((await reserve(9)).status, "cached");
    await db.query("update public.sugerencia_refuerzo_ia set version_enfoque=1 where id_estudiante=$1 and eje='GEOMETRIA'", [child]);
    const legacy = await reserve(5);
    assert.equal(legacy.status, "reserved", "la sugerencia anterior puede reemplazarse aunque no haya cinco respuestas nuevas");
    await db.query("select public.cancelar_sugerencia_ia($1,'GEOMETRIA',$2)", [child, legacy.lease]);
    await db.query("update public.sugerencia_refuerzo_ia set version_enfoque=2 where id_estudiante=$1 and eje='GEOMETRIA'", [child]);
    const next = await reserve(10);
    assert.equal(next.status, "reserved");
    await db.query("select public.cancelar_sugerencia_ia($1,'GEOMETRIA',$2)", [child, next.lease]);
    const retry = await reserve(10);
    assert.equal(retry.status, "reserved");
    await db.query("select public.cancelar_sugerencia_ia($1,'GEOMETRIA',$2)", [child, retry.lease]);
    await db.exec("reset role");
    for (const file of ["20261113002000_refuerzo_por_materia_y_nivel.sql", "20261117001000_decision_docente.sql"]) {
      await db.exec(await Deno.readTextFile(new URL("../migrations/" + file, import.meta.url)));
    }
    await db.exec("set role service_role");
    const signature = "a".repeat(64);
    const reserveV3 = async (n: number, key: string) => (await db.query<{ r: {status: string; lease: string} }>(
      "select public.reservar_sugerencia_v3($1,'GEOMETRIA',$2,$3) as r", [child, n, key])).rows[0].r;
    const reservation = await reserveV3(5, signature);
    assert.equal(reservation.status, "reserved");
    assert.equal((await db.query<{ ok: boolean }>("select public.finalizar_sugerencia_v3($1,'GEOMETRIA',$2,5,'Figuras','2/5 correctas','Bajar la dificultad (Inicial).',$3) as ok",
      [child, reservation.lease, signature])).rows[0].ok, true);
    assert.equal((await reserveV3(6, signature)).status, "cached");
    assert.equal((await reserveV3(6, "b".repeat(64))).status, "reserved");
    await db.exec("reset role");
    await db.exec(await Deno.readTextFile(new URL("../migrations/20261117002000_sugerencias_a_peticion.sql", import.meta.url)));
    const requestGeneration = async () => (await db.query<{ r: {status: string; lease: string} }>(
      "select public.reservar_sugerencia_docente($1,'GEOMETRIA') as r", [child])).rows[0].r;
    assert.equal((await requestGeneration()).status, "busy");
    await db.query("update public.sugerencia_refuerzo_ia set reserva_hasta=now() where id_estudiante=$1", [child]);
    const explicit = await requestGeneration();
    assert.equal(explicit.status, "reserved");
    assert.equal((await db.query<{ ok: boolean }>(
      "select public.finalizar_sugerencia_v3($1,'GEOMETRIA',$2,5,'Figuras','2/5 correctas','Bajar la dificultad (Inicial).',$3) as ok",
      [child, explicit.lease, signature])).rows[0].ok, true);
    assert.equal((await requestGeneration()).status, "reserved", "la misma materia admite otra generación inmediata");
    await db.exec(await Deno.readTextFile(new URL("../migrations/20261117003000_sugerencia_global.sql", import.meta.url)));
    await db.exec(await Deno.readTextFile(new URL("../migrations/20261118001000_sintesis_global_docente.sql", import.meta.url)));
    const global = (await db.query<{ r: {status: string; lease: string} }>(
      "select public.reservar_sugerencia_docente($1,'GLOBAL') as r", [child])).rows[0].r;
    assert.equal(global.status, "reserved");
    assert.equal((await db.query<{ ok: boolean }>(
      "select public.finalizar_sugerencia_v3($1,'GLOBAL',$2,20,'Próxima práctica',repeat('e',500),repeat('a',1000),$3) as ok",
      [child, global.lease, signature])).rows[0].ok, true);
    await assert.rejects(() => db.query("select public.finalizar_sugerencia_v3($1,'GEOMETRIA',$2,20,'Figuras','1/5',repeat('a',281),$3)",
      [child, global.lease, signature]));
    await db.query("delete from public.estudiante where id_estudiante=$1", [child]);
    assert.equal((await db.query("select * from public.sugerencia_refuerzo_ia")).rows.length, 0);
  } finally { await db.close(); }
});
