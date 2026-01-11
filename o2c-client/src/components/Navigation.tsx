import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export function Navigation() {
  const location = useLocation();
  const { state, logout } = useAuth();

  const showControlPanel =
    state.status === 'authenticated' &&
    ((state.roles || []).includes('SUPER_ADMIN') || (state.roles || []).includes('ADMIN'));

  const isActive = (path: string) => {
    if (path === '/') {
      return location.pathname === '/';
    }
    return location.pathname.startsWith(path);
  };

  return (
    <nav className="bg-white border-b border-gray-200 shadow-sm">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between items-center h-16">
          <div className="flex items-center">
            <Link to="/" className="mr-8">
              <h1 className="text-xl text-gray-900">Order To Cash</h1>
            </Link>
            <div className="flex space-x-4">
              <Link
                to="/create"
                className={`px-3 py-2 rounded-md transition-colors ${
                  isActive('/create')
                    ? 'bg-blue-100 text-blue-700'
                    : 'text-gray-700 hover:bg-gray-100'
                }`}
              >
                Create Order
              </Link>
              <Link
                to="/orders"
                className={`px-3 py-2 rounded-md transition-colors ${
                  isActive('/orders')
                    ? 'bg-blue-100 text-blue-700'
                    : 'text-gray-700 hover:bg-gray-100'
                }`}
              >
                Orders
              </Link>

              {showControlPanel && (
                <Link
                  to="/control-panel"
                  className={`px-3 py-2 rounded-md transition-colors ${
                    isActive('/control-panel')
                      ? 'bg-blue-100 text-blue-700'
                      : 'text-gray-700 hover:bg-gray-100'
                  }`}
                >
                  Control Panel
                </Link>
              )}
            </div>
          </div>

          <div className="flex items-center gap-3">
            {state.status === 'authenticated' ? (
              <>
                <span className="text-sm text-gray-600">{state.username}</span>
                <button
                  type="button"
                  onClick={() => logout()}
                  className="px-3 py-2 rounded-md text-gray-700 hover:bg-gray-100"
                >
                  Logout
                </button>
              </>
            ) : (
              <Link
                to="/login"
                className="px-3 py-2 rounded-md text-gray-700 hover:bg-gray-100"
              >
                Login
              </Link>
            )}
          </div>
        </div>
      </div>
    </nav>
  );
}
