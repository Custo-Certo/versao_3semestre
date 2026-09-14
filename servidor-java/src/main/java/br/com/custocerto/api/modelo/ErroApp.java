package br.com.custocerto.api.modelo;

/** Excecao que carrega o status HTTP a devolver (400, 404, 409...). */
public class ErroApp extends RuntimeException {
    public final int status;

    public ErroApp(String msg, int status) {
        super(msg);
        this.status = status;
    }
}
