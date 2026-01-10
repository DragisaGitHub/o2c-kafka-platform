import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import {Navigation} from "../components/Navigation";
import {CreateOrder} from "../pages/CreateOrder";
import {Orders} from "../pages/Orders";
import {OrderDetails} from "../pages/OrderDetails";
import { Login } from '../pages/Login';
import { AuthProvider } from '../auth/AuthContext';
import { RequireAuth } from '../auth/RequireAuth';

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <div className="min-h-screen bg-gray-50">
          <Navigation />
          <Routes>
            <Route path="/login" element={<Login />} />

            <Route
              path="/"
              element={
                <RequireAuth>
                  <Navigate to="/orders" replace />
                </RequireAuth>
              }
            />
            <Route
              path="/create"
              element={
                <RequireAuth>
                  <CreateOrder />
                </RequireAuth>
              }
            />
            <Route
              path="/orders"
              element={
                <RequireAuth>
                  <Orders />
                </RequireAuth>
              }
            />
            <Route
              path="/orders/:orderId"
              element={
                <RequireAuth>
                  <OrderDetails />
                </RequireAuth>
              }
            />

            <Route path="*" element={<Navigate to="/orders" replace />} />
          </Routes>
        </div>
      </AuthProvider>
    </BrowserRouter>
  );
}
