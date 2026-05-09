import { BrowserRouter, Routes, Route, NavLink } from 'react-router-dom';
import { lazy, Suspense } from 'react';

const LiveMapPage = lazy(() => import('./pages/LiveMapPage'));
const ZoneEditorPage = lazy(() => import('./pages/ZoneEditorPage'));

function PageLoader() {
  return (
    <div className="flex h-screen items-center justify-center bg-[#0a0d14]">
      <div className="flex flex-col items-center gap-3">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-gray-700 border-t-amber-500" />
        <span className="text-xs uppercase tracking-widest text-gray-500">Loading</span>
      </div>
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      {/* Top Nav */}
      <nav
        aria-label="Main navigation"
        className="fixed top-0 z-[2000] flex h-10 w-full items-center gap-1 border-b border-gray-800 bg-[#0a0d14]/95 px-4 backdrop-blur-sm"
      >
        <span className="mr-4 font-mono text-sm font-bold tracking-tight text-amber-400">
          HAULIO
        </span>
        <NavLink
          to="/"
          end
          aria-label="Live Map"
          className={({ isActive }) =>
            [
              'rounded-md px-3 py-1 text-xs font-medium transition-colors',
              'focus-visible:outline focus-visible:outline-2 focus-visible:outline-amber-400',
              isActive
                ? 'bg-amber-500/20 text-amber-300'
                : 'text-gray-400 hover:bg-gray-800 hover:text-gray-200',
            ].join(' ')
          }
        >
          Live Map
        </NavLink>
        <NavLink
          to="/zones"
          aria-label="Zone Editor"
          className={({ isActive }) =>
            [
              'rounded-md px-3 py-1 text-xs font-medium transition-colors',
              'focus-visible:outline focus-visible:outline-2 focus-visible:outline-amber-400',
              isActive
                ? 'bg-amber-500/20 text-amber-300'
                : 'text-gray-400 hover:bg-gray-800 hover:text-gray-200',
            ].join(' ')
          }
        >
          Zone Editor
        </NavLink>
      </nav>

      {/* Page Content */}
      <main className="pt-10 h-screen">
        <Suspense fallback={<PageLoader />}>
          <Routes>
            <Route path="/" element={<LiveMapPage />} />
            <Route path="/zones" element={<ZoneEditorPage />} />
          </Routes>
        </Suspense>
      </main>
    </BrowserRouter>
  );
}
