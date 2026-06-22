import React from "react";

type ErrorBoundaryState = {
  error: Error | null;
};

export class ErrorBoundary extends React.Component<
  React.PropsWithChildren,
  ErrorBoundaryState
> {
  state: ErrorBoundaryState = {
    error: null
  };

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error };
  }

  render() {
    if (this.state.error) {
      return (
        <main className="flex min-h-screen items-center justify-center bg-slate-950 p-6 text-white">
          <section className="w-full max-w-md rounded-lg border border-red-400/30 bg-red-950/20 p-5 shadow-xl">
            <h1 className="text-lg font-semibold">Frontend error</h1>
            <p className="mt-2 text-sm text-red-100">
              Aplikasi gagal render. Coba reload halaman atau restart frontend dev server.
            </p>
            <pre className="mt-4 max-h-48 overflow-auto rounded bg-black/30 p-3 text-xs text-red-100">
              {this.state.error.message}
            </pre>
            <button
              className="mt-4 h-10 rounded bg-cyan-500 px-4 text-sm font-bold text-white"
              type="button"
              onClick={() => window.location.reload()}
            >
              Reload
            </button>
          </section>
        </main>
      );
    }

    return this.props.children;
  }
}
