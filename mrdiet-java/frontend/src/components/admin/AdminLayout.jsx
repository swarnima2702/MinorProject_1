import React from "react";
import { ThemeToggle } from "@/components/ThemeToggle";
import { Link, useLocation } from "react-router-dom";
import {
  LayoutDashboard,
  Users,
  Settings,
  LogOut,
  CreditCard,
  UserCircle,
  Menu,
  Tag,
  Receipt,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetTrigger } from "@/components/ui/sheet";

const AdminLayout = ({ children }) => {
  const location = useLocation();
  const [config, setConfig] = React.useState(window.APP_CONFIG || {});
  const [isDark, setIsDark] = React.useState(
    document.documentElement.classList.contains("dark"),
  );
  const [open, setOpen] = React.useState(false);

  React.useEffect(() => {
    const handleThemeLoad = () => {
      setConfig(window.APP_CONFIG || {});
      setIsDark(document.documentElement.classList.contains("dark"));
    };

    window.addEventListener("theme-loaded", handleThemeLoad);
    return () => window.removeEventListener("theme-loaded", handleThemeLoad);
  }, []);

  const menuItems = [
    { label: "Dashboard", path: "/admin", icon: LayoutDashboard },
    { label: "Transactions", path: "/admin/transactions", icon: Receipt },
    { label: "Coupons", path: "/admin/coupons", icon: Tag },
    { label: "Withdrawals", path: "/admin/withdrawals", icon: CreditCard },
    { label: "Users", path: "/admin/users", icon: Users },
    { label: "User Profiles", path: "/admin/user-profiles", icon: UserCircle },
    { label: "Global Settings", path: "/admin/settings", icon: Settings },
  ];

  const SidebarContent = () => (
    <div className="flex flex-col h-full bg-surface-deep">
      <div className="p-8 border-b border-ink/5">
        <Link to="/" className="flex flex-col">
          <span className="text-2xl font-black text-gold tracking-tighter leading-none italic">
            mrdiet.
          </span>
          <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-[0.3em] mt-1 ml-1 font-mono">
            Admin Portal
          </span>
        </Link>
      </div>

      <nav className="p-4 space-y-1.5 flex-1 mt-4">
        <p className="text-[10px] font-black text-muted-foreground/30 uppercase tracking-widest mb-4 ml-4">
          Main Menu
        </p>
        {menuItems.map((item) => {
          const isActive = location.pathname === item.path;
          return (
            <Link key={item.path} to={item.path} onClick={() => setOpen(false)}>
              <Button
                variant="ghost"
                className={`w-full justify-start group h-11 mb-0.5 px-4 rounded-lg transition-all ${
                  isActive
                    ? "bg-gold/10 text-gold border border-gold/20 shadow-xl shadow-black/40"
                    : "hover:bg-ink/5 text-muted-foreground hover:text-foreground"
                }`}
              >
                <div className="flex items-center gap-3">
                  <item.icon className="h-4 w-4" />
                  <span className="font-bold text-[11px] uppercase tracking-wider">
                    {item.label}
                  </span>
                </div>
              </Button>
            </Link>
          );
        })}
      </nav>

      <div className="px-4 pb-2">
        <p className="text-[10px] font-black text-muted-foreground/60 uppercase tracking-widest mb-2 ml-1">
          Theme
        </p>
        <ThemeToggle showLabels className="w-full" />
      </div>

      <div className="p-4 border-t border-ink/5">
        <Button
          variant="ghost"
          className="w-full justify-start gap-3 h-11 rounded-lg text-red-500 hover:text-red-600 dark:hover:text-red-400 hover:bg-red-500/5 transition-all font-bold text-[11px] uppercase tracking-wider"
          onClick={() => {
            localStorage.removeItem("mrdiet_auth_user");
            window.location.href = "/auth";
          }}
        >
          <LogOut className="h-4 w-4" />
          Sign Out
        </Button>
      </div>
    </div>
  );

  return (
    <div className="flex min-h-screen bg-surface-deep text-foreground selection:bg-gold/30">
      {/* Mobile Header */}
      <header className="lg:hidden fixed top-0 left-0 right-0 h-16 bg-surface-deep/90 backdrop-blur-xl border-b border-ink/5 z-50 flex items-center justify-between px-6">
        <span className="text-xl font-black text-gold italic">mrdiet.</span>
        <Sheet open={open} onOpenChange={setOpen}>
          <SheetTrigger asChild>
            <Button variant="ghost" size="icon">
              <Menu className="h-5 w-5 text-ink" />
            </Button>
          </SheetTrigger>
          <SheetContent
            side="left"
            className="p-0 w-72 bg-surface-deep border-r border-ink/10"
          >
            <SidebarContent />
          </SheetContent>
        </Sheet>
      </header>

      {/* Desktop Sidebar - Fixed */}
      <aside className="w-72 border-r border-ink/5 bg-surface-deep fixed h-screen hidden lg:flex flex-col z-40">
        <SidebarContent />
      </aside>
      {/* Spacer for fixed sidebar */}
      <div className="hidden lg:block w-72 shrink-0" />

      {/* Main Content */}
      <main className="flex-1 overflow-x-hidden min-h-screen pt-20 lg:pt-0">
        <div className="p-6 lg:p-12 max-w-7xl mx-auto">{children}</div>
      </main>
    </div>
  );
};

export default AdminLayout;
